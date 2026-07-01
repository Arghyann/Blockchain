import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Node {

    protected final int p2pPort;
    protected final int httpPort;
    protected final Blockchain blockchain;
    protected final ECC.KeyPair wallet;
    protected final String name;

    // Thread-safe list of active peer sockets we can broadcast to
    protected final List<Socket> peerSockets = new CopyOnWriteArrayList<>();
    protected final List<Integer> peerPorts;
    protected final List<ECC.Point> knownAddresses = new CopyOnWriteArrayList<>();
    protected final List<String> auditLog = new CopyOnWriteArrayList<>();
    protected void logAudit(String msg) {
        auditLog.add(msg);
        System.out.println("⚠️ [Audit Warning] Node " + name + ": " + msg);
    }
    public Node(String name, int p2pPort, int httpPort, List<Integer> peerPorts) {
        this.name = name;
        this.p2pPort = p2pPort;
        this.httpPort = httpPort;
        this.peerPorts = peerPorts;
        
        // We use a lower difficulty (e.g. 3) for the live network to make mining fast in containers
        this.blockchain = new Blockchain(3); 
        this.wallet = ECC.generateKeyPair();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: java Node <name> <p2pPort> <httpPort> <peerPort1> <peerPort2> ...");
            System.exit(1);
        }

        String name = args[0];
        int p2pPort = Integer.parseInt(args[1]);
        int httpPort = Integer.parseInt(args[2]);
        List<Integer> peerPorts = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            peerPorts.add(Integer.parseInt(args[i]));
        }

        Node node = new Node(name, p2pPort, httpPort, peerPorts);
        node.start();
    }

    public void start() throws Exception {
        System.out.println("Starting Node [" + name + "]...");
        System.out.println("P2P listening on port: " + p2pPort);
        System.out.println("HTTP API listening on port: " + httpPort);
        System.out.println("Wallet Public Key: " + wallet.publicKey.toString().substring(0, 30) + "...");

        // 1. Start P2P Server Thread
        new Thread(this::startP2PServer).start();

        // 2. Start Peer Connector Thread (connects to target peer ports)
        new Thread(this::connectToPeers).start();

        // 3. Start HTTP API Server
        startHttpServer();

        // 4. Start Ecosystem Simulator
        new Thread(this::runEcosystemSimulation).start();
    }

    // ==========================================
    // EXERCISE 5.1: Peer Message Processing
    // ==========================================
    private void handlePeerMessage(String message, Socket socket) {
        // Announce our public key to the peer
        sendToSocket(socket, "ANNOUNCE " + wallet.publicKey.x.toString(16) + "," + wallet.publicKey.y.toString(16));
        try {
            String[] parts = message.split(" ", 2);
            String command = parts[0];
            String payload = parts.length > 1 ? parts[1] : "";

            switch (command) {
                case "ANNOUNCE":
                    String[] coords = payload.split(",");
                    ECC.Point peerKey = new ECC.Point(new BigInteger(coords[0], 16), new BigInteger(coords[1], 16));
                    if (!knownAddresses.contains(peerKey) && !peerKey.equals(wallet.publicKey)) {
                        knownAddresses.add(peerKey);
                        System.out.println("Discovered peer address: " + peerKey.toString().substring(0, 25) + "...");
                    }
                    break;
                case "GET_CHAIN":
                    // Peer is requesting our blockchain.
                    // Send them our full chain using the CHAIN command.
                    System.out.println("Peer requested our chain. Sending...");
                    String chainPayload;
                    synchronized (blockchain) {
                        chainPayload = "CHAIN " + serializeChain(blockchain.chain);
                    }
                    sendToSocket(socket, chainPayload);
                    break;

                case "CHAIN":
                    List<Blockchain.Block> receivedChain = deserializeChain(payload);
                    System.out.println("Received chain of size " + receivedChain.size());
                    if (receivedChain.size() > blockchain.chain.size()) {
                        synchronized (blockchain) {
                            List<Blockchain.Block> originalChain =
                                    new ArrayList<>(blockchain.chain);

                            blockchain.chain.clear();
                            blockchain.chain.addAll(receivedChain);

                            if (blockchain.verifyChain()) {
                                System.out.println(
                                        "Chain verified and accepted! Switched to longer chain. New length: "
                                                + blockchain.chain.size());
                            } else {
                                System.out.println(
                                        "Received chain is INVALID! Restoring original chain.");
                                logAudit("[REJECTED CHAIN] Received chain of size " + receivedChain.size() + " failed verification.");
                                blockchain.chain.clear();
                                blockchain.chain.addAll(originalChain);
                            }
                        }
                    }
                    break;

                case "TX":
                    Blockchain.Transaction tx = deserializeTx(payload);
                    boolean alreadySeen = false;
                    synchronized (blockchain) {
                        for (Blockchain.Transaction pending : blockchain.pendingTransactions) {
                            if (pending.calculateHash().equals(tx.calculateHash())) {
                                alreadySeen = true;
                                break;
                            }
                        }
                        if (!alreadySeen) {
                            System.out.println("Received transaction: " + tx);
                            try {
                                blockchain.addTransaction(tx);
                            } catch (Exception e) {
                                String sender = (tx.senderPubkey == null) ? "coinbase" : tx.senderPubkey.toString().substring(0, 25) + "...";
                                logAudit("[REJECTED TX] Sender: " + sender + " | Reason: " + e.getMessage());
                                alreadySeen = true; // Mark as seen to prevent forwarding invalid transactions
                            }
                        }
                    }
                    if (!alreadySeen) {
                        broadcast("TX " + payload);
                    }
                    break;

                case "BLOCK":
                    Blockchain.Block block = deserializeBlock(payload);
                    System.out.println("Received block with hash: " + block.hash);

                    synchronized (blockchain) {
                        // Already have it?
                        boolean haveit = false;
                        for (Blockchain.Block b : blockchain.chain) {
                            if (b.hash.equals(block.hash)) {
                                haveit = true;
                                break;
                            }
                        }
                        if (haveit) {
                            break;
                        }

                        Blockchain.Block latest =
                                blockchain.chain.get(blockchain.chain.size() - 1);

                        if (!block.previousHash.equals(latest.hash)) {
                            logAudit("[FORKED BLOCK] Hash: " + block.hash.substring(0, 10) + "... | Out-of-sync: previousHash mismatch. Requested sync.");
                            sendToSocket(socket, "GET_CHAIN");
                            break;
                        }
 
                        if (!block.hash.equals(block.calculateHash())) {
                            logAudit("[REJECTED BLOCK] Hash: " + block.hash.substring(0, 10) + "... | Reason: Invalid block hash (tampering).");
                            break;
                        }
 
                        if (!block.hash.startsWith("0".repeat(blockchain.difficulty))) {
                            logAudit("[REJECTED BLOCK] Hash: " + block.hash.substring(0, 10) + "... | Reason: Block difficulty too low.");
                            break;
                        }

                        boolean badTransaction = false;
                        String failReason = "";
                        for (Blockchain.Transaction t : block.transactions) {
                            if (!t.verify()) {
                                badTransaction = true;
                                failReason = "Invalid transaction signature";
                                break;
                            }
                            if (t.senderPubkey == null && t.amount != Blockchain.COINBASE_REWARD) {
                                badTransaction = true;
                                failReason = "Invalid coinbase reward: " + t.amount;
                                break;
                            }
                        }
                        if (badTransaction) {
                            logAudit("[REJECTED BLOCK] Hash: " + block.hash.substring(0, 10) + "... | Reason: Contains bad transaction: " + failReason);
                            break;
                        }

                        blockchain.chain.add(block);
                        blockchain.pendingTransactions.removeAll(block.transactions);
                        broadcast("BLOCK " + payload);
                        System.out.println("Successfully appended block!");
                    }
                    break;

            }
        } catch (Exception e) {
            System.err.println("Error processing peer message: " + e.getMessage());
        }
    }

    // ==========================================
    // P2P Network Socket Layer
    // ==========================================
    private void startP2PServer() {
        try (ServerSocket serverSocket = new ServerSocket(p2pPort)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                peerSockets.add(clientSocket);
                new Thread(() -> handlePeerConnection(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("P2P Server error: " + e.getMessage());
        }
    }

    private void handlePeerConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            // Request peer's chain to sync on initial connection
            sendToSocket(socket, "GET_CHAIN");

            String line;
            while ((line = in.readLine()) != null) {
                handlePeerMessage(line, socket);
            }
        } catch (IOException e) {
            System.out.println("Connection with peer lost.");
        } finally {
            peerSockets.remove(socket);
        }
    }

    private void connectToPeers() {
        while (true) {
            for (int port : peerPorts) {
                // Check if already connected to this port
                if (isAlreadyConnected(port)) continue;

                try {
                    // Try connecting to peer container on localhost (or peer hostname in Docker network)
                    Socket socket = new Socket("127.0.0.1", port);
                    peerSockets.add(socket);
                    System.out.println("Connected to peer on port " + port);
                    new Thread(() -> handlePeerConnection(socket)).start();
                } catch (IOException e) {
                    // Fail silently, peer might not be online yet
                }
            }
            try {
                Thread.sleep(5000); // retry connect every 5 seconds
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private boolean isAlreadyConnected(int port) {
        for (Socket s : peerSockets) {
            if (s.getPort() == port && s.isConnected()) return true;
        }
        return false;
    }

    protected void broadcast(String message) {
        for (Socket socket : peerSockets) {
            sendToSocket(socket, message);
        }
    }

    protected void sendToSocket(Socket socket, String message) {
        try {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println(message);
        } catch (IOException e) {
            peerSockets.remove(socket);
        }
    }

    // ==========================================
    // HTTP API Layer (Built-in JDK Server)
    // ==========================================
    private void startHttpServer() throws Exception {
        HttpServer server = HttpServer.create(new java.net.InetSocketAddress(httpPort), 0);
        
        server.createContext("/status", exchange -> {
            String response = "Node: " + name + "\n" +
                    "Chain Length: " + blockchain.chain.size() + "\n" +
                    "Latest Block Hash: " + blockchain.getLatestBlock().hash + "\n" +
                    "Pending Pool Size: " + blockchain.pendingTransactions.size() + "\n" +
                    "Wallet Balance: " + blockchain.getBalance(wallet.publicKey) + " BTC-lite\n" +
                    "Wallet Address (Public Key X): " + wallet.publicKey.x.toString(16) + "\n";
            sendHttpResponse(exchange, 200, response);
        });

        server.createContext("/attempts", exchange -> {
            StringBuilder sb = new StringBuilder();
            sb.append("=== REJECTED ATTEMPTS AND AUDIT LOG ===\n");
            for (String log : auditLog) {
                sb.append(log).append("\n");
            }
            sendHttpResponse(exchange, 200, sb.toString());
        });

        server.createContext("/mine", exchange -> {
            synchronized (blockchain) {
                blockchain.minePendingTransactions(wallet.publicKey);
            }
            Blockchain.Block latest = blockchain.getLatestBlock();
            broadcast("BLOCK " + serializeBlock(latest));
            sendHttpResponse(exchange, 200, "Block successfully mined and broadcasted!\nNew Hash: " + latest.hash + "\n");
        });

        server.createContext("/send", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendHttpResponse(exchange, 405, "Method not allowed. Use POST.\n");
                return;
            }
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
                String body = reader.readLine();
                // format: receiverX,receiverY;amount
                String[] parts = body.split(";");
                String[] rCoords = parts[0].split(",");
                ECC.Point receiver = new ECC.Point(new BigInteger(rCoords[0], 16), new BigInteger(rCoords[1], 16));
                double amount = Double.parseDouble(parts[1]);

                Blockchain.Transaction tx = new Blockchain.Transaction(wallet.publicKey, receiver, amount);
                tx.sign(wallet.privateKey);

                synchronized (blockchain) {
                    blockchain.addTransaction(tx);
                }

                broadcast("TX " + serializeTx(tx));
                sendHttpResponse(exchange, 200, "Transaction submitted and broadcasted! Hash: " + tx.calculateHash() + "\n");
            } catch (Exception e) {
                sendHttpResponse(exchange, 400, "Invalid transaction request: " + e.getMessage() + "\n");
            }
        });

        server.createContext("/chain", exchange -> {
            String response = serializeChain(blockchain.chain);
            sendHttpResponse(exchange, 200, response + "\n");
        });

        server.setExecutor(null);
        server.start();
    }

    private void sendHttpResponse(HttpExchange exchange, int status, String response) throws IOException {
        exchange.sendResponseHeaders(status, response.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    // ==========================================
    // TEXT SERIALIZATION METHODS
    // ==========================================

    protected static String serializeTx(Blockchain.Transaction tx) {
        String sender = (tx.senderPubkey == null || tx.senderPubkey.isInfinity()) ? "coinbase" 
                : tx.senderPubkey.x.toString(16) + "," + tx.senderPubkey.y.toString(16);
        String receiver = tx.receiverPubkey.x.toString(16) + "," + tx.receiverPubkey.y.toString(16);
        String sig = (tx.signature == null) ? "none" : tx.signature.r.toString(16) + "," + tx.signature.s.toString(16);
        return sender + ";" + receiver + ";" + tx.amount + ";" + sig;
    }

    private static Blockchain.Transaction deserializeTx(String str) {
        String[] parts = str.split(";");
        ECC.Point sender = null;
        if (!parts[0].equals("coinbase")) {
            String[] sCoords = parts[0].split(",");
            sender = new ECC.Point(new BigInteger(sCoords[0], 16), new BigInteger(sCoords[1], 16));
        }
        String[] rCoords = parts[1].split(",");
        ECC.Point receiver = new ECC.Point(new BigInteger(rCoords[0], 16), new BigInteger(rCoords[1], 16));
        double amount = Double.parseDouble(parts[2]);
        Blockchain.Transaction tx = new Blockchain.Transaction(sender, receiver, amount);
        if (!parts[3].equals("none")) {
            String[] sigParts = parts[3].split(",");
            tx.signature = new ECC.Signature(new BigInteger(sigParts[0], 16), new BigInteger(sigParts[1], 16));
        }
        return tx;
    }

    protected static String serializeBlock(Blockchain.Block block) {
        StringBuilder sb = new StringBuilder();
        sb.append(block.previousHash).append(";");
        sb.append(block.timestamp).append(";");
        sb.append(block.nonce).append(";");
        sb.append(block.hash).append(";");
        for (int i = 0; i < block.transactions.size(); i++) {
            sb.append(serializeTx(block.transactions.get(i)));
            if (i < block.transactions.size() - 1) {
                sb.append("|");
            }
        }
        return sb.toString();
    }

    private static Blockchain.Block deserializeBlock(String str) {
        String[] parts = str.split(";", 5);
        String previousHash = parts[0];
        long timestamp = Long.parseLong(parts[1]);
        long nonce = Long.parseLong(parts[2]);
        String hash = parts[3];
        List<Blockchain.Transaction> txs = new ArrayList<>();
        if (parts.length > 4 && !parts[4].trim().isEmpty()) {
            String[] txStrings = parts[4].split("\\|");
            for (String txStr : txStrings) {
                txs.add(deserializeTx(txStr));
            }
        }
        Blockchain.Block block = new Blockchain.Block(txs, previousHash, timestamp);
        block.nonce = nonce;
        block.hash = hash;
        return block;
    }

    private static String serializeChain(List<Blockchain.Block> chain) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chain.size(); i++) {
            sb.append(serializeBlock(chain.get(i)));
            if (i < chain.size() - 1) {
                sb.append("#");
            }
        }
        return sb.toString();
    }

    private static List<Blockchain.Block> deserializeChain(String str) {
        List<Blockchain.Block> newChain = new ArrayList<>();
        String[] blockStrings = str.split("#");
        for (String blockStr : blockStrings) {
            newChain.add(deserializeBlock(blockStr));
        }
        return newChain;
    }
    private void runEcosystemSimulation() {
        // Wait 10 seconds for peers to connect and exchange addresses
        try { Thread.sleep(10000); } catch (InterruptedException e) {}

        java.util.Random rand = new java.util.Random();

        // 1. Continuous Mining Thread (Grants rewards and processes pending transactions)
        new Thread(() -> {
            while (true) {
                try {
                    // Sleep randomly between 8 to 15 seconds to simulate blocks
                    Thread.sleep(8000 + rand.nextInt(7000));

                    synchronized (blockchain) {
                        System.out.println("\n[Miner] Starting background mining round...");
                        blockchain.minePendingTransactions(wallet.publicKey);
                        broadcast("BLOCK " + serializeBlock(blockchain.getLatestBlock()));
                    }
                } catch (Exception e) {
                    System.err.println("Mining error: " + e.getMessage());
                }
            }
        }).start();

        // 2. Transaction Generator Thread (Spends money once earned)
        new Thread(() -> {
            while (true) {
                try {
                    // Generate a transaction every 12 to 20 seconds
                    Thread.sleep(12000 + rand.nextInt(8000));

                    double myBalance = blockchain.getBalance(wallet.publicKey);
                    // Only send if we have funds and know of other peers
                    if (myBalance > 10 && !knownAddresses.isEmpty()) {
                        ECC.Point recipient = knownAddresses.get(rand.nextInt(knownAddresses.size()));
                        double amount = 1 + rand.nextInt(5); // send random amount between 1 and 5

                        System.out.println("\n[Wallet] Auto-generating transaction: Sending " + amount + " BTC-lite to peer address...");

                        Blockchain.Transaction tx = new Blockchain.Transaction(wallet.publicKey, recipient, amount);
                        tx.sign(wallet.privateKey);

                        synchronized (blockchain) {
                            blockchain.addTransaction(tx);
                        }
                        broadcast("TX " + serializeTx(tx));
                    }
                } catch (Exception e) {
                    System.err.println("Tx generator error: " + e.getMessage());
                }
            }
        }).start();
    }
}
