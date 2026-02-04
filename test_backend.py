#!/usr/bin/env python3
"""
Dummy Backend for Testing Photo Uploads
Receives photos from MyCyanGlasses app and saves them locally
"""

from flask import Flask, request, jsonify
import os
from datetime import datetime
from pathlib import Path

app = Flask(__name__)

# Create uploads directory
UPLOAD_FOLDER = Path(__file__).parent / 'backend_uploads'
UPLOAD_FOLDER.mkdir(exist_ok=True)

@app.route('/upload', methods=['POST'])
def upload_photo():
    """Receive photo upload from glasses app"""

    print("\n" + "="*60)
    print(f"📸 Photo Upload Received at {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("="*60)

    # Log headers
    print("\nHeaders:")
    for key, value in request.headers.items():
        if key.lower() == 'authorization':
            print(f"  {key}: {value[:20]}..." if len(value) > 20 else f"  {key}: {value}")
        else:
            print(f"  {key}: {value}")

    # Log form data
    print("\nForm Data:")
    for key, value in request.form.items():
        print(f"  {key}: {value}")

    # Process uploaded file
    if 'photo' not in request.files:
        print("❌ No photo file in request")
        return jsonify({
            'success': False,
            'error': 'No photo file provided'
        }), 400

    photo = request.files['photo']

    if photo.filename == '':
        print("❌ Empty filename")
        return jsonify({
            'success': False,
            'error': 'Empty filename'
        }), 400

    # Save the photo
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    filename = f"{timestamp}_{photo.filename}"
    filepath = UPLOAD_FOLDER / filename

    photo.save(str(filepath))
    file_size = os.path.getsize(filepath)

    print(f"\n✓ Photo saved successfully!")
    print(f"  Filename: {filename}")
    print(f"  Size: {file_size:,} bytes ({file_size/1024:.1f} KB)")
    print(f"  Path: {filepath}")
    print("="*60 + "\n")

    # Return success response
    return jsonify({
        'success': True,
        'message': 'Photo uploaded successfully',
        'filename': filename,
        'size': file_size,
        'received_at': datetime.now().isoformat()
    }), 200

@app.route('/health', methods=['GET'])
def health():
    """Health check endpoint"""
    return jsonify({
        'status': 'ok',
        'message': 'Dummy backend is running',
        'upload_count': len(list(UPLOAD_FOLDER.glob('*.jpg'))) + len(list(UPLOAD_FOLDER.glob('*.jpeg')))
    }), 200

@app.route('/', methods=['GET'])
def index():
    """Root endpoint with info"""
    upload_count = len(list(UPLOAD_FOLDER.glob('*.jpg'))) + len(list(UPLOAD_FOLDER.glob('*.jpeg')))

    return f"""
    <html>
    <head><title>MyCyanGlasses Test Backend</title></head>
    <body style="font-family: Arial; padding: 40px; max-width: 800px; margin: 0 auto;">
        <h1>📸 MyCyanGlasses Test Backend</h1>
        <p>Backend is running and ready to receive photos!</p>

        <h2>Status</h2>
        <ul>
            <li><strong>Upload Endpoint:</strong> POST /upload</li>
            <li><strong>Photos Received:</strong> {upload_count}</li>
            <li><strong>Storage Location:</strong> {UPLOAD_FOLDER}</li>
        </ul>

        <h2>How to Use</h2>
        <ol>
            <li>Get your computer's IP address (run <code>hostname -I</code> on Linux)</li>
            <li>In the app Settings, set endpoint to: <code>http://YOUR_IP:5000/upload</code></li>
            <li>Make sure your phone and computer are on the same network</li>
            <li>Take photos with the glasses - they'll be saved to backend_uploads/</li>
        </ol>

        <h2>Quick Test</h2>
        <p>Test the connection from your app using the Test Connection button</p>
    </body>
    </html>
    """

if __name__ == '__main__':
    print("\n" + "="*60)
    print("🚀 Starting MyCyanGlasses Dummy Backend")
    print("="*60)
    print(f"📁 Upload folder: {UPLOAD_FOLDER.absolute()}")
    print(f"🌐 Server will run on: http://0.0.0.0:5000")
    print("\n📱 To use with your Android app:")
    print("   1. Find your computer's IP address")
    print("   2. In app Settings, set endpoint to: http://YOUR_IP:5000/upload")
    print("   3. Take photos and they'll be saved locally!")
    print("\n💡 Press Ctrl+C to stop the server")
    print("="*60 + "\n")

    app.run(host='0.0.0.0', port=5000, debug=True)
