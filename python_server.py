import socket
import threading
import sys

# Multi-Client TCP Broadcast Socket Server for TP11
HOST = '0.0.0.0'
PORT = 8888

clients = []
clients_lock = threading.Lock()

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

def broadcast(message, sender_socket):
    with clients_lock:
        for client in clients:
            if client != sender_socket:
                try:
                    client.sendall(message.encode('utf-8') + b'\n')
                except Exception:
                    pass

def handle_client(client_socket, client_address):
    print(f"[+] New connection from {client_address}")
    with clients_lock:
        clients.append(client_socket)

    # Send welcome message
    try:
        welcome_msg = f"Connected to Desktop Python Server ({get_local_ip()}:{PORT})\n"
        client_socket.sendall(welcome_msg.encode('utf-8'))
    except Exception:
        pass

    buffer = ""
    try:
        while True:
            data = client_socket.recv(1024)
            if not data:
                break

            buffer += data.decode('utf-8', errors='ignore')
            while '\n' in buffer:
                line, buffer = buffer.split('\n', 1)
                line = line.strip()
                if line:
                    formatted_msg = f"[{client_address[0]}:{client_address[1]}]: {line}"
                    print(formatted_msg)
                    broadcast(formatted_msg, client_socket)

    except Exception as e:
        print(f"[-] Error handling client {client_address}: {e}")
    finally:
        with clients_lock:
            if client_socket in clients:
                clients.remove(client_socket)
        try:
            client_socket.close()
        except Exception:
            pass
        print(f"[-] Connection closed with {client_address}")

def main():
    local_ip = get_local_ip()
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    try:
        server.bind((HOST, PORT))
        server.listen(5)
        print("=" * 60)
        print(" TP11 TCP Multi-Client Python Server")
        print(f" Server running on Local IP: {local_ip}")
        print(f" Listening on Port: {PORT}")
        print(" Enter this IP and Port on your Android app's Socket Chat tab!")
        print("=" * 60)

        while True:
            client_socket, client_address = server.accept()
            client_thread = threading.Thread(
                target=handle_client,
                args=(client_socket, client_address),
                daemon=True
            )
            client_thread.start()

    except KeyboardInterrupt:
        print("\n[*] Server stopping...")
    except Exception as e:
        print(f"[!] Server exception: {e}")
    finally:
        server.close()
        sys.exit(0)

if __name__ == "__main__":
    main()
