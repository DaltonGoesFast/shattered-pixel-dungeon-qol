import asyncio
import websockets
import json
from datetime import datetime

async def connect_to_game():
    uri = "ws://127.0.0.1:5001"
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] Attempting to connect to {uri}...")
    
    try:
        async with websockets.connect(uri) as websocket:
            print("Successfully connected! Listening for game state updates...\n")
            
            while True:
                # Wait for the JSON message from the game
                message = await websocket.recv()
                
                # Parse the JSON string into a Python dictionary
                data = json.loads(message)
                
                # Match game snapshot shape: hero.class, hero.subclass, hero.hp, hero.ht; stats are in 'stats'
                hero = data.get('hero') or {}
                stats = data.get('stats') or {}
                if not hero:
                    print("Update Received -> No active run (title / menu)")
                    continue
                hero_class = hero.get('class', 'Unknown')
                subclass = hero.get('subclass')
                hero_name = f"{hero_class} ({subclass})" if subclass else hero_class
                hp = hero.get('hp', 0)
                ht = hero.get('ht', 0)
                depth = stats.get('depth', 0)
                gold = stats.get('gold', 0)
                print(f"Update Received -> Hero: {hero_name} | HP: {hp}/{ht} | Depth: {depth} | Gold: {gold}")
                
                # Uncomment the line below if you want to see the full raw JSON
                # print(json.dumps(data, indent=2))

    except ConnectionRefusedError:
        print("Error: Could not connect. Is the game running with the server enabled?")
    except websockets.exceptions.ConnectionClosed:
        print("\nConnection lost. The game was likely closed.")
    except Exception as e:
        print(f"An unexpected error occurred: {e}")

if __name__ == "__main__":
    try:
        asyncio.run(connect_to_game())
    except KeyboardInterrupt:
        print("\nClient stopped by user.")