import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class MaliciousNode extends Node {

    public MaliciousNode(String name, int p2pPort, int httpPort, List<Integer> peerPorts) {
        super(name, p2pPort, httpPort, peerPorts);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.out.println("Usage: java MaliciousNode <name> <p2pPort> <httpPort> <peerPort1> <peerPort2> ...");
            System.exit(1);
        }

        String name = args[0];
        int p2pPort = Integer.parseInt(args[1]);
        int httpPort = Integer.parseInt(args[2]);
        List<Integer> peerPorts = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            peerPorts.add(Integer.parseInt(args[i]));
        }

        MaliciousNode node = new MaliciousNode(name, p2pPort, httpPort, peerPorts);
        node.start();
    }

    @Override
    public void start() throws Exception {
        // Start standard P2P server and Peer Connector threads from superclass
        super.start();

        System.out.println("👿 WARNING: MALICIOUS NODE [" + name + "] IS ONLINE! 👿");

        // Start background malicious threads
        new Thread(this::runMaliciousSimulation).start();
    }

    private void runMaliciousSimulation() {
        try { Thread.sleep(15000); } catch (InterruptedException e) {}

        java.util.Random rand = new java.util.Random();

        // Thread 1: Mine Blocks with Inflated Coinbase Reward (5000.0 instead of 50.0)
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(10000 + rand.nextInt(5000));
                    
                    synchronized (blockchain) {
                        System.out.println("\n👿 [Malloy] Mining block with INFLATED reward (5000.0)...");
                        
                        // Overwrite coinbase transaction with 5000.0 coins
                        Blockchain.Transaction inflatedCoinbase = new Blockchain.Transaction(null, wallet.publicKey, 5000.0);
                        
                        List<Blockchain.Transaction> blockTxs = new ArrayList<>(blockchain.pendingTransactions);
                        blockTxs.add(inflatedCoinbase);

                        Blockchain.Block badBlock = new Blockchain.Block(blockTxs, blockchain.getLatestBlock().hash, System.currentTimeMillis());
                        badBlock.mineBlock(blockchain.difficulty);
                        
                        blockchain.chain.add(badBlock);
                        blockchain.pendingTransactions.clear();
                        
                        System.out.println("👿 [Malloy] Inflated block mined! Broadcasting...");
                        broadcast("BLOCK " + serializeBlock(badBlock));
                    }
                } catch (Exception e) {
                    System.err.println("Malicious mining error: " + e.getMessage());
                }
            }
        }).start();

        // Thread 2: Broadcast Forged Signature Transactions (Steal Alice's Coins)
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(15000 + rand.nextInt(10000));
                    
                    // Generate a random public key to pretend to be Alice
                    // and sign with a fake signature (garbage).
                    ECC.KeyPair fakeAlice = ECC.generateKeyPair();
                    
                    System.out.println("\n👿 [Malloy] Broadcasting forged transaction claiming to be from Alice...");
                    
                    Blockchain.Transaction forgedTx = new Blockchain.Transaction(fakeAlice.publicKey, wallet.publicKey, 100.0);
                    // Create a garbage signature
                    forgedTx.signature = new ECC.Signature(BigInteger.valueOf(123456789), BigInteger.valueOf(987654321));

                    broadcast("TX " + serializeTx(forgedTx));
                } catch (Exception e) {
                    // ignore
                }
            }
        }).start();

        // Thread 3: Broadcast Double Spend Transactions (Spend funds twice concurrently)
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(18000 + rand.nextInt(7000));
                    
                    double myBalance = blockchain.getBalance(wallet.publicKey);
                    if (myBalance > 15 && !knownAddresses.isEmpty()) {
                        // Pick two random recipients
                        ECC.Point recipient1 = knownAddresses.get(rand.nextInt(knownAddresses.size()));
                        ECC.Point recipient2 = knownAddresses.get(rand.nextInt(knownAddresses.size()));
                        
                        double amount = myBalance - 5; // attempt to spend almost all funds twice!
                        
                        System.out.println("\n👿 [Malloy] Attempting double spend: sending " + amount + " to two peers concurrently...");
                        
                        // Transaction 1
                        Blockchain.Transaction tx1 = new Blockchain.Transaction(wallet.publicKey, recipient1, amount);
                        tx1.sign(wallet.privateKey);
                        
                        // Transaction 2 (Double Spend)
                        Blockchain.Transaction tx2 = new Blockchain.Transaction(wallet.publicKey, recipient2, amount);
                        tx2.sign(wallet.privateKey);
                        
                        // Broadcast both concurrently
                        broadcast("TX " + serializeTx(tx1));
                        broadcast("TX " + serializeTx(tx2));
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        }).start();
    }
}
