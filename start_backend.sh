#!/bin/bash

echo "================================================"
echo "MyCyanGlasses Test Backend Starter"
echo "================================================"
echo ""

# Check if Python is installed
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 is not installed. Please install Python 3 first."
    exit 1
fi

# Check if Flask is installed
if ! python3 -c "import flask" 2>/dev/null; then
    echo "⚠️  Flask is not installed. Installing now..."
    pip install flask || pip3 install flask
    echo ""
fi

# Get local IP address
echo "🌐 Your computer's IP addresses:"
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    hostname -I | tr ' ' '\n' | grep -E '^192\.|^10\.|^172\.' || echo "Could not detect IP"
elif [[ "$OSTYPE" == "darwin"* ]]; then
    ifconfig | grep "inet " | grep -v 127.0.0.1 | awk '{print $2}'
else
    echo "Please find your IP manually"
fi
echo ""
echo "📱 In the app Settings, use: http://YOUR_IP:5000/upload"
echo ""
echo "Press Ctrl+C to stop the server"
echo "================================================"
echo ""

# Start the backend
python3 test_backend.py
