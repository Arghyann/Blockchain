import java.math.BigInteger;

public class TestBlockchain {

    public static void main(String[] args) {
        System.out.println("Running Blockchain validation tests...");
        boolean allPassed = true;

        // Initialize wallets
        ECC.KeyPair alice = ECC.generateKeyPair();
        ECC.KeyPair aryan = ECC.generateKeyPair();

        // 1. Test Hashing and Mining
        try {
            int difficulty = 3;
            Blockchain bc = new Blockchain(difficulty);
            
            // Check Genesis Block mining
            String genesisHash = bc.chain.get(0).hash;
            String target = "0".repeat(difficulty);
            if (!genesisHash.startsWith(target)) {
                System.err.println("❌ Test 1 Failed: Genesis block was not mined correctly! Hash: " + genesisHash);
                allPassed = false;
            } else {
                System.out.println("✅ Test 1 Passed: Genesis block successfully mined with difficulty " + difficulty);
            }
        } catch (Exception e) {
            System.err.println("❌ Test 1 Failed with exception: " + e.getMessage());
            e.printStackTrace();
            allPassed = false;
        }

        // 2. Test Balance Calculation & Mining
        try {
            int difficulty = 3;
            Blockchain bc = new Blockchain(difficulty);
            
            // Alice mines 3 blocks
            bc.minePendingTransactions(alice.publicKey); // Block 1 (mined by Alice, reward 50)
            bc.minePendingTransactions(alice.publicKey); // Block 2 (mined by Alice, reward 50)
            bc.minePendingTransactions(alice.publicKey); // Block 3 (mined by Alice, reward 50)

            double aliceBalance = bc.getBalance(alice.publicKey);
            if (aliceBalance != 150.0) {
                System.err.println("❌ Test 2 Failed: Balance calculation error. Expected Alice to have 150.0, but had: " + aliceBalance);
                allPassed = false;
            } else {
                System.out.println("✅ Test 2 Passed: Miner reward transactions mined and balances computed correctly.");
            }

            // 3. Test Transaction Signature and pool addition
            Blockchain.Transaction tx = new Blockchain.Transaction(alice.publicKey, aryan.publicKey, 100.0);
            tx.sign(alice.privateKey);
            
            String txHash = tx.calculateHash();
            if (txHash == null || txHash.length() != 64) {
                System.err.println("❌ Test 3 Failed: Transaction hash is invalid: '" + txHash + "'");
                allPassed = false;
            } else {
                System.out.println("✅ Test 3 Passed: Transaction hash calculated successfully (Hash: " + txHash.substring(0, 10) + "...).");
            }

            bc.addTransaction(tx);
            
            // Pending pool debit check (Alice should have 50 left unconfirmed)
            double aliceUnconfirmed = bc.getBalance(alice.publicKey);
            if (aliceUnconfirmed != 50.0) {
                System.err.println("❌ Test 3.1 Failed: Balance did not subtract pending transactions. Alice balance: " + aliceUnconfirmed);
                allPassed = false;
            } else {
                System.out.println("✅ Test 3.1 Passed: Pending pool balance subtraction works.");
            }

            // Mine block containing the transaction
            bc.minePendingTransactions(aryan.publicKey); // Mined by Aryan, he gets 50 reward

            double aliceFinal = bc.getBalance(alice.publicKey);
            double aryanFinal = bc.getBalance(aryan.publicKey);

            if (aliceFinal != 50.0 || aryanFinal != 150.0) { // Aryan: 100 from Alice + 50 reward
                System.err.println("❌ Test 3.2 Failed: Final balances incorrect. Alice: " + aliceFinal + ", Aryan: " + aryanFinal);
                allPassed = false;
            } else {
                System.out.println("✅ Test 3.2 Passed: Transaction block mined and wallet balances are correct.");
            }

            // 4. Verify Chain
            boolean chainValid = bc.verifyChain();
            if (!chainValid) {
                System.err.println("❌ Test 4 Failed: verifyChain() failed on a valid, un-tampered blockchain!");
                allPassed = false;
            } else {
                System.out.println("✅ Test 4 Passed: verifyChain() validated a clean blockchain.");
            }

            // 5. Test Double-Spending check in addTransaction
            try {
                Blockchain.Transaction badTx = new Blockchain.Transaction(alice.publicKey, aryan.publicKey, 60.0); // Alice only has 50 left
                badTx.sign(alice.privateKey);
                bc.addTransaction(badTx);
                System.err.println("❌ Test 5 Failed: Allowed Alice to double spend 60.0 when she only has 50.0!");
                allPassed = false;
            } catch (IllegalArgumentException e) {
                System.out.println("✅ Test 5 Passed: Successfully blocked double-spend transaction in addTransaction().");
            }

            // 6. Test Tampering Detection
            int blockIndex = bc.chain.size() - 1;
            Blockchain.Block lastBlock = bc.chain.get(blockIndex);
            
            // Find Alice -> Aryan transaction in the block and tamper with it
            for (int i = 0; i < lastBlock.transactions.size(); i++) {
                Blockchain.Transaction t = lastBlock.transactions.get(i);
                if (alice.publicKey.equals(t.senderPubkey)) {
                    // Tamper with the amount
                    Blockchain.Transaction fakeTx = new Blockchain.Transaction(t.senderPubkey, t.receiverPubkey, 500.0);
                    fakeTx.signature = t.signature; // keep same signature
                    
                    lastBlock.transactions.set(i, fakeTx);
                    break;
                }
            }

            boolean isTamperedChainValid = bc.verifyChain();
            if (isTamperedChainValid) {
                System.err.println("❌ Test 6 Failed: verifyChain() did not detect tampered transaction amount!");
                allPassed = false;
            } else {
                System.out.println("✅ Test 6 Passed: verifyChain() successfully detected tampered transaction content.");
            }

        } catch (Exception e) {
            System.err.println("❌ Blockchain tests failed with unexpected exception: " + e.getMessage());
            e.printStackTrace();
            allPassed = false;
        }

        System.out.println("=================================================");
        if (allPassed) {
            System.out.println("🎉 ALL VALIDATION TESTS PASSED! You are a Blockchain wizard!");
        } else {
            System.out.println("⚠️ SOME TESTS FAILED. Please review your implementation in Blockchain.java.");
        }
        System.out.println("=================================================");
    }
}
