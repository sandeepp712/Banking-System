#!/bin/bash
# Banking System - WSL Setup Script
# Run once to configure environment

set -e  # Exit on error

echo "🔧 Setting up Banking System environment..."

# Ensure we're in WSL
if ! grep -qi microsoft /proc/version 2>/dev/null; then
    echo "⚠️  Warning: This script is optimized for WSL"
fi

# Set project root
export PROJECTS=${PROJECTS:-$HOME/projects}
export BANKING_PROJECT=$PROJECTS/banking-system

# Create directories
mkdir -p $BANKING_PROJECT/{src/{main,test}/java/com/bank/{domain,service,persistence,concurrency},scripts,docs}

# Create .gitignore if missing
if [ ! -f $BANKING_PROJECT/.gitignore ]; then
    cat > $BANKING_PROJECT/.gitignore << 'EOF'
target/
*.class
*.log
txn.log
snapshot.dat
.idea/
.vscode/
.DS_Store
EOF
fi

echo "✅ Setup complete!"
echo "📁 Project location: $BANKING_PROJECT"
echo "🚀 Next: cd $BANKING_PROJECT && mvn clean compile"