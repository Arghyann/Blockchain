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
            // TODO: Implement Transaction Hashing
            //
            // 1. Get the message bytes for the core transaction data using getSigningData().
            //    Convert it to string using: new String(getSigningData(), StandardCharsets.UTF_8)
            // 2. If the signature is not null, append its parts: ";" + signature.r.toString(16) + "," + signature.s.toString(16)
            // 3. Compute the SHA-256 digest of this combined string (in UTF-8 bytes).
            //    Hint: MessageDigest.getInstance("SHA-256").digest(inputBytes)
            // 4. Return the hash as a hexadecimal string (padded with leading zeros if necessary).
            
            return ""; // Placeholder
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
            // TODO: Implement Block Header Hashing
            //
            // 1. Initialize a StringBuilder.
            // 2. Append:
            //    - previousHash
            //    - Long.toString(timestamp)
            //    - Long.toString(nonce)
            // 3. Loop through 'transactions' list and append the calculateHash() of each transaction.
            // 4. Compute the SHA-256 digest of this combined string's UTF-8 bytes.
            // 5. Return the digest as a hexadecimal string.
            
            return ""; // Placeholder
        }

        /**
         * EXERCISE 3.2: Proof of Work Mining Loop
         * Increments nonce until block hash starts with 'difficulty' number of leading zeros.
         * 
         * Proof of work forces miners to spend computing power (electricity) to secure the network.
         * The difficulty specifies how many leading hexadecimal zeros the hash must have.
         */
        public void mineBlock(int difficulty) {
            // TODO: Implement the Mining Loop
            //
            // 1. Create a target string containing 'difficulty' number of zeros.
            //    Hint: "0".repeat(difficulty)
            // 2. Compute the initial block hash using calculateHash() and store it in this.hash.
            // 3. Run a loop: while the block hash does not start with the target string:
            //    - Increment this.nonce.
            //    - Recalculate this.hash using calculateHash().
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
        // TODO: Implement Balance Calculation
        //
        // 1. Initialize balance = 0.0.
        // 2. Loop through all blocks in 'chain':
        //    - Loop through all transactions in each block:
        //      - If address.equals(tx.receiverPubkey), add tx.amount to balance.
        //      - If address.equals(tx.senderPubkey), subtract tx.amount from balance.
        // 3. Loop through all transactions in 'pendingTransactions' (the pool):
        //      - If address.equals(tx.senderPubkey), subtract tx.amount from balance.
        //        (This prevents a user from double-spending their pending unconfirmed coins!)
        // 4. Return balance.
        
        return 0.0; // Placeholder
    }

    /**
     * Validates signature and balance, then adds transaction to the pending pool.
     */
    public void addTransaction(Transaction tx) {
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
        // TODO: Implement Blockchain Integrity Verification
        //
        // Part A: Linkage, Hashing, and PoW validation
        // 1. Loop through the chain starting from index 1 (skip the genesis block for linkage):
        //    - Let 'current' be the block at index i, and 'previous' be the block at index i-1.
        //    - Verify current.hash matches current.calculateHash(). If not, print error and return false.
        //    - Verify current.previousHash matches previous.hash. If not, print error and return false.
        //    - Verify current.hash starts with difficulty number of zeros: "0".repeat(difficulty)
        //      If not, print error and return false.
        //
        // Part B: Transaction Validity and Balance Integrity (Prevent Double-Spends)
        // 2. Initialize a Map<ECC.Point, Double> balances = new HashMap<>();
        // 3. Loop through all blocks in 'chain' (from index 0 to size-1):
        //    - Loop through all transactions inside the block:
        //      - Verify the transaction signature by calling tx.verify(). If false, print error and return false.
        //      - Update balances:
        //        - If tx.senderPubkey is not null:
        //          - Get sender balance (default 0.0). If balance < tx.amount, print error (double-spend) and return false.
        //          - Subtract amount from sender.
        //        - Get receiver balance (default 0.0). Add amount to receiver.
        //
        // 4. Return true if all checks succeed!

        return false; // Placeholder
    }
}
