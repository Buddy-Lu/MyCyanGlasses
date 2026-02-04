# Test Backend Setup for MyCyanGlasses

This guide explains how to run a dummy backend to test the photo upload feature before your real backend is ready.

## Option 1: Python Flask Backend (Recommended)

### Requirements
- Python 3.6+
- Flask

### Setup

1. **Install Flask:**
```bash
pip install flask
```

2. **Run the backend:**
```bash
python test_backend.py
```

The server will start on `http://0.0.0.0:5000`

3. **Find your computer's IP address:**

**Linux/Mac:**
```bash
hostname -I
# Or
ifconfig | grep "inet "
```

**Windows:**
```bash
ipconfig
```

Look for your local IP (usually starts with `192.168.` or `10.0.`)

4. **Configure the app:**
   - Open MyCyanGlasses app
   - Go to Backend Settings
   - Enter: `http://YOUR_IP:5000/upload` (replace YOUR_IP with your actual IP)
   - Example: `http://192.168.1.100:5000/upload`
   - Tap "Test Connection" to verify
   - Tap "Save"

5. **Test photo uploads:**
   - Take a photo with the glasses
   - Check the terminal - you should see upload logs
   - Photos are saved in `backend_uploads/` folder
   - Open the photos to verify they uploaded correctly!

### What You'll See

Terminal output:
```
============================================================
📸 Photo Upload Received at 2026-02-03 14:35:12
============================================================

Headers:
  Content-Type: multipart/form-data
  Authorization: Bearer your-api-key (if configured)

Form Data:
  timestamp: 1738566912345
  filename: photo_20260203_143512.jpg

✓ Photo saved successfully!
  Filename: 20260203_143512_photo_20260203_143512.jpg
  Size: 245,678 bytes (239.9 KB)
  Path: /home/buddy/.../backend_uploads/20260203_143512_photo_20260203_143512.jpg
============================================================
```

### Viewing Uploads

All uploaded photos are saved in the `backend_uploads/` folder in your project directory. You can open them to verify the uploads are working correctly.

### Health Check

Visit `http://YOUR_IP:5000` in a browser to see the backend status and upload count.

## Option 2: Using httpbin.org (No Setup Required)

The app defaults to `https://httpbin.org/post` which is a free testing service. It works great for testing but doesn't save photos.

**Pros:**
- No setup required
- Works immediately
- Good for testing connectivity

**Cons:**
- Doesn't save photos
- Can't verify photo content
- Requires internet connection

## Option 3: ngrok (For Remote Testing)

If your phone and computer are on different networks, use ngrok to create a public URL:

1. **Install ngrok:** https://ngrok.com/download

2. **Run the Flask backend:**
```bash
python test_backend.py
```

3. **In another terminal, run ngrok:**
```bash
ngrok http 5000
```

4. **Use the ngrok URL in app settings:**
   - ngrok will show a URL like: `https://abc123.ngrok.io`
   - In app Settings, enter: `https://abc123.ngrok.io/upload`

## Troubleshooting

### "Connection failed" error

1. **Check firewall:** Make sure port 5000 is not blocked
   ```bash
   # Linux
   sudo ufw allow 5000
   ```

2. **Check same network:** Phone and computer must be on the same WiFi

3. **Try IP address directly:** Use the actual IP, not "localhost" or "127.0.0.1"

### "No route to host" error

- Your computer's firewall may be blocking connections
- Try temporarily disabling the firewall
- Make sure Flask is running on `0.0.0.0`, not `127.0.0.1`

### Photos not saving

- Check folder permissions for `backend_uploads/`
- Check terminal for error messages
- Verify the backend received the request (check terminal logs)

## Testing Checklist

- [ ] Backend server is running
- [ ] Computer's IP address is correct
- [ ] Phone and computer are on same network
- [ ] App Settings configured with correct endpoint
- [ ] "Test Connection" passes in Settings
- [ ] Take a photo with glasses
- [ ] Check terminal for upload log
- [ ] Verify photo saved in `backend_uploads/` folder
- [ ] Open saved photo to verify it's correct

## Next Steps

Once your buddy finishes the real backend, just change the endpoint URL in Settings. The upload format will be the same:

- **Method:** POST
- **Content-Type:** multipart/form-data
- **Fields:**
  - `photo` - Image file (JPEG)
  - `timestamp` - Unix timestamp (milliseconds)
  - `filename` - Original filename
- **Headers:** Optional `Authorization: Bearer <api-key>`

The real backend should accept the same format, so no app changes will be needed!
