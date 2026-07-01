#!/bin/bash

while true; do
  clear
  echo "================================================="
  echo "      LIVE BTC-LITE BLOCKCHAIN MONITOR (ALICE)   "
  echo "================================================="
  
  # Fetch chain from Alice
  chain=$(curl -s http://localhost:8081/chain)
  
  if [ -z "$chain" ]; then
    echo "Waiting for Node Alice (8081) to start..."
  else
    # Split chain by '#' into blocks
    # Disable pathname expansion (globbing) and split
    set -f
    IFS='#' read -r -a blocks <<< "$chain"
    set +f
    
    echo "Current Chain Height: ${#blocks[@]} blocks"
    echo "-------------------------------------------------"
    
    # Print the last 3 blocks to keep output clean and readable
    start=0
    if [ ${#blocks[@]} -gt 3 ]; then
      start=$((${#blocks[@]} - 3))
    fi
    
    for ((i=start; i<${#blocks[@]}; i++)); do
      block="${blocks[i]}"
      
      # Block format: previousHash;timestamp;nonce;hash;transactions
      set -f
      IFS=';' read -r -a parts <<< "$block"
      set +f
      
      # Extract the first 4 fields
      prev=$(echo "$block" | cut -d';' -f1)
      ts=$(echo "$block" | cut -d';' -f2)
      nonce=$(echo "$block" | cut -d';' -f3)
      hash=$(echo "$block" | cut -d';' -f4)
      # Get everything after the 4th semicolon (the transactions payload)
      txs=$(echo "$block" | cut -d';' -f5-)
      
      echo "Block #$i"
      echo "  Hash:      $hash"
      echo "  Prev Hash: $prev"
      echo "  Nonce:     $nonce"
      
      if [ -n "$txs" ] && [ "$txs" != " " ] && [ "$txs" != "" ]; then
        # Count transactions by splitting by '|'
        set -f
        IFS='|' read -r -a txList <<< "$txs"
        set +f
        echo "  Transactions (${#txList[@]}):"
        for tx in "${txList[@]}"; do
          # Parse transaction details
          set -f
          IFS=';' read -r -a txParts <<< "$tx"
          set +f
          txSender="${txParts[0]}"
          txReceiver="${txParts[1]}"
          txAmount="${txParts[2]}"
          
          # Shorten addresses for neat printing
          senderShort="Coinbase"
          if [ "$txSender" != "coinbase" ]; then
            senderShort="${txSender:0:25}..."
          fi
          receiverShort="${txReceiver:0:25}..."
          
          echo "    - $senderShort sends $txAmount BTC-lite to $receiverShort"
        done
      else
        echo "  Transactions (0)"
      fi
      echo "-------------------------------------------------"
    done
  fi

  # Fetch attempts from Alice (8081)
  attempts=$(curl -s http://localhost:8081/attempts)
  if [ -n "$attempts" ]; then
    echo ""
    echo "================================================="
    echo "         SECURITY MONITOR (REJECTED ATTEMPTS)    "
    echo "================================================="
    # Print the last 5 logs from attempts
    echo "$attempts" | tail -n 5
  fi

  sleep 2
done
