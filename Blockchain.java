import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Blockchain {

    public static class Transaction {
        public final ECC.Point senderPubkey;   // null for coinbase/reward
        public final ECC.Point receiverPubkey;
        public final double amount;
        public ECC.Signature signature;

        public Transaction(ECC.Point senderPubkey, ECC.Point receiverPubkey, double amount) {
            this.senderPubkey = senderPubkey;
            this.receiverPubkey = receiverPubkey;
            this.amount = amount;
        }

        /**
         * Serializes the transaction's core data (excluding signature) to bytes for signing.
         */
        public byte[] getSigningData() {
            String senderStr = (senderPubkey == null || senderPubkey.isInfinity()) ? "coinbase" 
                    : senderPubkey.x.toString(16) + "," + senderPubkey.y.toString(16);
            String receiverStr = (receiverPubkey == null || receiverPubkey.isInfinity()) ? "none" 
                    : receiverPubkey.x.toString(16) + "," + receiverPubkey.y.toString(16);
            
            String data = senderStr + ";" + receiverStr + ";" + amount;
            return data.getBytes(StandardCharsets.UTF_8);
        }

        /**
         * Signs the transaction with the sender's private key.
         */
        public void sign(BigInteger privateKey) {
            if (senderPubkey == null) {
                throw new IllegalStateException("Coinbase transactions cannot be signed.");
            }
            this.signature = ECC.sign(getSigningData(), privateKey);
        }

        /**
         * Verifies the signature of the transaction.
         */
        public boolean verify() {
            if (senderPubkey == null) {
                return true; // Coinbase transaction needs no signature
            }
            if (signature == null) {
                return false;
            }
            return ECC.verify(getSigningData(), signature, senderPubkey);
        }

        /**
         * EXERCISE 2: Transaction Hashing
         * Returns SHA-256 hash of transaction data (including signature if present) as a hex string.
         *
         * Why hash? This hash serves as the transaction ID (txid) and is used to chain inputs/outputs
         * and verify data integrity.
         */
        public String calculateHash() {
            try {
                String data = new String(getSigningData(), StandardCharsets.UTF_8);

                if (signature != null) {
                    data += ";" + signature.r.toString(16)
                            + "," + signature.s.toString(16);
                }

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));

                return String.format("%064x", new BigInteger(1, hash));
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        @Override
        public String toString() {
            String sender = (senderPubkey == null) ? "Coinbase" : senderPubkey.toString().substring(0, 25) + "...";
            String receiver = receiverPubkey.toString().substring(0, 25) + "...";
            return "Tx[" + sender + " -> " + receiver + " : " + amount + "]";
        }
    }

    public static class Block {
        public final List<Transaction> transactions;
        public final String previousHash;
        public final long timestamp;
        public long nonce;
        public String hash;

        public Block(List<Transaction> transactions, String previousHash, long timestamp) {
            this.transactions = transactions;
            this.previousHash = previousHash;
            this.timestamp = timestamp;
            this.nonce = 0;
            this.hash = calculateHash();
        }

        /**
         * EXERCISE 3.1: Block Header Hashing
         * Computes SHA-256 hash of the Block Header and transactions list.
         * 
         * In a real blockchain, transactions are hashed into a Merkle Tree root, and only the 
         * Merkle Root is stored in the header. For simplicity, we can concatenate the previousHash, 
         * timestamp, nonce, and the hash of each transaction, and hash the resulting string.
         */
        public String calculateHash() {
            try {
                StringBuilder sb = new StringBuilder();

                sb.append(previousHash);
                sb.append(timestamp);
                sb.append(nonce);

                for (Transaction tx : transactions) {
                    sb.append(tx.calculateHash());
                }

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));

                return String.format("%064x", new BigInteger(1, hash));

            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * EXERCISE 3.2: Proof of Work Mining Loop
         * Increments nonce until block hash starts with 'difficulty' number of leading zeros.
         * 
         * Proof of work forces miners to spend computing power (electricity) to secure the network.
         * The difficulty specifies how many leading hexadecimal zeros the hash must have.
         */
        public void mineBlock(int difficulty) {
            String target = "0".repeat(difficulty);
            hash = calculateHash();
            while(!hash.startsWith(target)){
                nonce++;
                hash=calculateHash();
            }
        }
    }

    public final List<Block> chain;
    public final List<Transaction> pendingTransactions;
    public final int difficulty;
    public static final double COINBASE_REWARD = 50.0;

    public Blockchain(int difficulty) {
        this.chain = new ArrayList<>();
        this.pendingTransactions = new ArrayList<>();
        this.difficulty = difficulty;
        
        // Assemble Genesis block
        Block genesis = new Block(new ArrayList<>(), "0", System.currentTimeMillis());
        genesis.mineBlock(difficulty);
        this.chain.add(genesis);
    }

    public Block getLatestBlock() {
        return chain.get(chain.size() - 1);
    }

    /**
     * EXERCISE 4.1: Wallet Balance Calculation
     * Calculates the balance of a wallet address by scanning the entire blockchain history 
     * and subtracting pending outgoing transactions.
     * 
     * In Bitcoin, balances are calculated using UTXOs (Unspent Transaction Outputs). In our
     * simplified balance-based ledger, we compute it on the fly by scanning all historical debits
     * and credits, and subtracting pending transactions to prevent double spending.
     */
    public double getBalance(ECC.Point address) {
        double balance = 0;
        for(Block b : chain){
            for(Transaction t: b.transactions){
                if (address.equals(t.receiverPubkey)) {
                    balance += t.amount;
                }

                if (address.equals(t.senderPubkey)) {
                    balance -= t.amount;
                }
            }
        }
        for (Transaction tx : pendingTransactions) {
            if (address.equals(tx.senderPubkey)) {
                balance -= tx.amount;
            }
        }

        return balance;
    }

    /**
     * Validates signature and balance, then adds transaction to the pending pool.
     */
    public void addTransaction(Transaction tx) {
        if (tx.senderPubkey == null) {
            throw new IllegalArgumentException("Coinbase transactions are not allowed in the pending transaction pool.");
        }

        if (!tx.verify()) {
            throw new IllegalArgumentException("Transaction signature verification failed.");
        }

        if (tx.senderPubkey != null) {
            double balance = getBalance(tx.senderPubkey);
            if (balance < tx.amount) {
                throw new IllegalArgumentException("Insufficient balance. Wallet has " + balance + ", attempted to send " + tx.amount);
            }
        }

        pendingTransactions.add(tx);
    }

    /**
     * Bundles pending transactions, appends coinbase reward, mines block, and appends to the chain.
     */
    public void minePendingTransactions(ECC.Point minerRewardAddress) {
        Transaction rewardTx = new Transaction(null, minerRewardAddress, COINBASE_REWARD);
        
        List<Transaction> blockTxs = new ArrayList<>(pendingTransactions);
        blockTxs.add(rewardTx);

        Block newBlock = new Block(blockTxs, getLatestBlock().hash, System.currentTimeMillis());
        System.out.println("Mining a new block... (Target difficulty: " + difficulty + ")");
        long startTime = System.currentTimeMillis();
        newBlock.mineBlock(difficulty);
        long endTime = System.currentTimeMillis();
        
        System.out.println("Block mined! Nonce: " + newBlock.nonce + ", Hash: " + newBlock.hash + " in " + (endTime - startTime) + "ms");
        
        chain.add(newBlock);
        pendingTransactions.clear();
    }

    /**
     * EXERCISE 4.2: Chain Integrity & Consensus Verification
     * Validates the integrity of the blockchain end-to-end.
     */
    public boolean verifyChain() {

        // Part A: Verify hashes, links, and Proof of Work
        for (int i = 1; i < chain.size(); i++) {
            Block current = chain.get(i);
            Block previous = chain.get(i - 1);

            // Verify stored hash matches calculated hash
            if (!current.hash.equals(current.calculateHash())) {
                System.out.println("Invalid block hash.");
                return false;
            }

            // Verify chain linkage
            if (!current.previousHash.equals(previous.hash)) {
                System.out.println("Broken chain linkage at block " + i + "!");
                System.out.println("  Current Block Hash: " + current.hash);
                System.out.println("  Current PreviousHash field: " + current.previousHash);
                System.out.println("  Previous Block Hash field: " + previous.hash);
                return false;
            }

            // Verify Proof of Work
            if (!current.hash.startsWith("0".repeat(difficulty))) {
                System.out.println("Invalid Proof of Work.");
                return false;
            }
        }

        // Part B: Verify transactions and balances
        Map<ECC.Point, Double> balances = new HashMap<>();

        for (Block block : chain) {
            for (Transaction tx : block.transactions) {

                // Verify transaction signature
                if (!tx.verify()) {
                    System.out.println("Invalid transaction signature.");
                    return false;
                }

                // Validate coinbase reward amount (prevent inflation)
                if (tx.senderPubkey == null) {
                    if (tx.amount != COINBASE_REWARD) {
                        System.out.println("Verification Error: Invalid coinbase reward amount: " + tx.amount);
                        return false;
                    }
                }

                // Coinbase transactions have no sender
                if (tx.senderPubkey != null) {

                    double senderBalance =
                            balances.getOrDefault(tx.senderPubkey, 0.0);

                    // Prevent overdrafts / double spending
                    if (senderBalance < tx.amount) {
                        System.out.println("Double-spend or insufficient balance detected.");
                        return false;
                    }

                    // Subtract from sender
                    balances.put(
                            tx.senderPubkey,
                            senderBalance - tx.amount
                    );
                }

                // Add to receiver
                double receiverBalance =
                        balances.getOrDefault(tx.receiverPubkey, 0.0);

                balances.put(
                        tx.receiverPubkey,
                        receiverBalance + tx.amount
                );
            }
        }

        return true;
    }}
