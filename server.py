#!/usr/bin/env python3
# imports tkinter (Toolkit interface), useful for the graphical user interface
import tkinter as tk	    			# Load tkinter with the name tk
# Python image Library, Pillow
from PIL import Image, ImageTk
# Flask is a WSGI (Web Server Gateaway Interface) web application framework, Module request -> access to HTTP Requests
from flask import Flask, request, jsonify
from werkzeug.exceptions import HTTPException
# So that Flask can run on a seperate Thread
import subprocess
import threading
import os
import json

### Global Variables (in order to use global variables, they must be declared with the keyword global inside a Function) ###
incoming_message = "" # Shared Memory between threads, Flask-Thread writes, Tkinter reads
new_background_path = None 
incoming_color = None  # Shared between Flask thread and Tkinter
incoming_brightness = None  # 0-100, shared between Flask thread and Tkinter

### Configuration ###
SCROLL_DELAY_TOP_MS = 2000
SCROLL_DELAY_BOTTOM_MS = 2500
SCROLL_STEP_PX = 12
SCROLL_TICK_MS = 1200
TOP_PAD = 6
WRAP_PAD = 20
w = 1024						# Display width
h = 100							# Display height

DEFAULT_TEXT_COLOR = "#00FF00"  # hacker green default

### Image Paths ###
UPLOAD_FOLDER = os.environ.get(
    "DISPLAYDOORSILL_DATA_DIR",
    os.path.join(os.path.expanduser("~"), ".local", "share", "DisplayDoorSill", "images"),
)
CURRENT_BG_PATH = os.path.join(UPLOAD_FOLDER, "current_background.jpg")
DEFAULT_BG_PATH = os.environ.get("DISPLAYDOORSILL_BACKGROUND", "")

INITIAL_TEXT = "Waiting for User Input"

### Create a folder if it doesnt exist ###
os.makedirs(UPLOAD_FOLDER, exist_ok=True)


### Flask setup ###
# Flask Server Configuration - Receiver
app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 10 * 1024 * 1024
HOST = os.environ.get("DISPLAYDOORSILL_HOST", "127.0.0.1")
PORT = 5000

# Endpoint for health check / ping from Android app
# Used to check if Pi is reachable before starting BLE provisioning
@app.route('/status', methods=['GET'])
def status():
    return jsonify({"success": True, "message": "Pi is running"}), 200


# @app.route() is a decorator, a function that extends the behaviour of another function, without changing it
# Endpoint that receives text from smartphone
@app.route('/send-text', methods = ['POST']) # If an HTTP request comes in with the URL "/send-text" and the Method "Post", wird diese Funktion aufgerufen
def receive_text():
    global incoming_message	# declare incoming_message as global
    try:
        # request: object from falsk, request.data: bytes from the request, .decode('utf-8'): Python Method 
        raw = request.data.decode('utf-8') # read and decode data package
        if not raw:
            # HTTP responde status codes 400 -> client error response, bad request
            return jsonify({"success": False, "message": "Empty"}), 400
        text = raw
        try:
            obj = json.loads(raw)
            if isinstance(obj, dict) and "text" in obj:
                text = obj["text"]
        except json.JSONDecodeError:
            print("[SERVER] JSON Decode Error")
            pass
        if not isinstance(text, str) or len(text) > 4096:
            return jsonify({"success": False, "message": "Text must be a string of at most 4096 characters"}), 400
        print("[SERVER] Text received")
		#Prepare Data
        incoming_message = text
		# Trigger Event, Root is an object from the class tk.Tk(), event_generate is a method of the class
		# UpdateDisplay is a virtual event binded with on_new_message_interrupt, when = tail means place in in the end of the queue
        root.event_generate('<<UpdateDisplay>>', when = "tail")
		# Successful HTTP response
        return jsonify({"success": True, "message": "Text received"}), 200
    except HTTPException:
        raise
    except Exception as e:	# if exception in try block return
        print("[SERVER] Text request failed")
        return jsonify({"success": False, "message": "Request failed"}), 500


# Endpoint that receives color hex from smartphone
@app.route('/set-color', methods=['POST'])
def receive_color():
    global incoming_color
    try:
        raw = request.data.decode('utf-8')
        if not raw:
            return jsonify({"success": False, "message": "Empty"}), 400

        color = raw
        try:
            obj = json.loads(raw)
            if isinstance(obj, dict) and "color" in obj:
                color = obj["color"]
        except json.JSONDecodeError:
            pass

        # Validate hex color format
        color = color.strip()
        if not color.startswith('#') or len(color) != 7:
            return jsonify({"success": False, "message": f"Invalid color: {color}"}), 400

        print(f"[SERVER] Color received: {color}")
        incoming_color = color
        root.event_generate('<<UpdateColor>>', when="tail")

        return jsonify({"success": True, "message": f"Color set to {color}"}), 200
    except HTTPException:
        raise
    except Exception as e:
        print("[SERVER] Color request failed")
        return jsonify({"success": False, "message": "Request failed"}), 500


# Endpoint that receives brightness (0-100) from smartphone
@app.route('/set-brightness', methods=['POST'])
def receive_brightness():
    global incoming_brightness
    try:
        raw = request.data.decode('utf-8')
        if not raw:
            return jsonify({"success": False, "message": "Empty"}), 400

        brightness = None
        try:
            obj = json.loads(raw)
            if isinstance(obj, dict) and "brightness" in obj:
                brightness = int(obj["brightness"])
        except (json.JSONDecodeError, ValueError):
            pass

        # Fallback: try parsing raw as int
        if brightness is None:
            try:
                brightness = int(raw.strip())
            except ValueError:
                return jsonify({"success": False, "message": f"Invalid brightness: {raw}"}), 400

        # Clamp to 0-100
        brightness = max(0, min(100, brightness))

        print(f"[SERVER] Brightness received: {brightness}%")
        incoming_brightness = brightness
        root.event_generate('<<UpdateBrightness>>', when="tail")

        return jsonify({"success": True, "message": f"Brightness set to {brightness}%"}), 200
    except HTTPException:
        raise
    except Exception as e:
        print("[SERVER] Brightness request failed")
        return jsonify({"success": False, "message": "Request failed"}), 500


### Endpoint that receives images from smartphone
@app.route('/upload', methods= ['POST'])
def receive_image():
    global new_background_path
    try:
        # check if file part exists in request
        if 'file' not in request.files:
            print("[SERVER] No file part in request")
            return "no file part", 400
        
        file = request.files['file']
        
        #check if file was selected
        if file.filename == '':
            print("[SERVER] No file selected")
            return "no file selected", 400
        
        # get filename (debug purposes)
        filename = request.form.get('filename', 'uploaded_image.jpg')
        print("[SERVER] Receiving image")
        
        # Delete old background if it exists
        if os.path.exists(CURRENT_BG_PATH):
            try:
                os.remove(CURRENT_BG_PATH)
                print(f"[SERVER] Deleted old background: {CURRENT_BG_PATH}")
            except Exception as e:
                print(f"[SERVER] could not delete old background: {e}")
                
        # save new image as current background
        file.save(CURRENT_BG_PATH)
        print(f"[SERVER] Saved new background: {CURRENT_BG_PATH}")
        
        # set flag and trigger GUI update
        new_background_path = CURRENT_BG_PATH
        root.event_generate('<<UpdateBackground>>', when="tail")
        
        return "OK", 200
    
    except HTTPException:
        raise
    except Exception as e:
        print("[SERVER] Image upload failed")
        return jsonify({"success": False, "message": "Image upload failed"}), 500
    
                
 	
# Start server in own thread, on port 5000, accessible to everyone
def start_server():
    # app.run opens a TCP Socket und connects to host:port
	# host 0.0.0.0 makes the server in local network visible, if not 0.0.0.0 WLAN, Ethernet etc. have no access
	# port 5000 is the standard flask port
	# use_reloader: code starts again automatically if changed
	# if use_reloader = true there could be more instances/processes of the server -> undefined behaviour
	app.run(host = HOST, port = PORT, debug = False, use_reloader = False)

### Tk setup ###
root = tk.Tk() 				# Create Main Window root
root.title("DisplayDoorSill")			# change title
root.overrideredirect(True)	# remove outline
#Screen Width and Height
root.geometry(f"{w}x{h}+0+0") 	# + 0 + 0 -> show in top left corner
root.configure(bg = 'black')	# Set background to black
# An alternative must be found, Keyboard Interrupt do not work, because the focus gets overwritten by the OS
root.focus_force()

### Background image (canvas) ###
# Determine whhich Background to use on startup
def get_initial_background_path():
    if os.path.exists(CURRENT_BG_PATH):
        print(f"[GUI] using saved background: {CURRENT_BG_PATH}")
        return CURRENT_BG_PATH
    elif os.path.exists(DEFAULT_BG_PATH):
        print(f"[GUI] using default background: {DEFAULT_BG_PATH}")
        return DEFAULT_BG_PATH
    else:
        print("[GUI] No background Image found, using black")
        return None
    
initial_bg_path = get_initial_background_path()

# Load initial background
if initial_bg_path:
    bg_image_pil = Image.open(initial_bg_path).resize((w, h), Image.LANCZOS)
    bg_image = ImageTk.PhotoImage(bg_image_pil)
else:
    # create black image as fallback
    bg_image_pil = Image.new('RGB', (w, h), color = 'black')
    bg_image = ImageTk.PhotoImage(bg_image_pil)



canvas = tk.Canvas(root, width = w, height = h, highlightthickness = 0, bd = 0)
canvas.place(x = 0, y = 0, width = w, height = h)

# Draw background image
bg_image_id = canvas.create_image(0, 0, anchor = "nw", image = bg_image) # pass reference of image -> no garbage collection danger

# Draw text on top
text_id = canvas.create_text(
    w // 2, 	# center horizontally (width : 2)
    TOP_PAD,	# y = TOP_PAD
    text = INITIAL_TEXT,
    fill = DEFAULT_TEXT_COLOR,
    font = ("Arial", 22, "bold"),
    width = w - WRAP_PAD,
    justify = "center",
    anchor = "n"	# north
)

### Brightness overlay ###
# Semi-transparent black rectangle on top of everything
# Tkinter canvas doesn't support true alpha, so we use a stipple pattern + solid overlay
# We use a Pillow-generated RGBA image instead for smooth dimming
brightness_overlay_image = None  # keep reference to prevent garbage collection
brightness_overlay_id = canvas.create_image(0, 0, anchor="nw")  # placeholder, initially invisible
canvas.tag_raise(brightness_overlay_id)  # always on top

def create_dimming_overlay(brightness_percent):
    """
    Creates a semi-transparent black overlay image.
    brightness 100 = fully visible (no overlay)
    brightness 0   = fully black
    """
    global brightness_overlay_image
    
    # alpha: 0 = transparent, 255 = fully opaque black
    alpha = int(255 * (1.0 - brightness_percent / 100.0))
    
    if alpha <= 0:
        # Full brightness -> hide overlay
        canvas.itemconfigure(brightness_overlay_id, state="hidden")
        return
    
    # Create RGBA image: black with calculated alpha
    overlay_pil = Image.new('RGBA', (w, h), (0, 0, 0, alpha))
    brightness_overlay_image = ImageTk.PhotoImage(overlay_pil)
    
    canvas.itemconfigure(brightness_overlay_id, image=brightness_overlay_image, state="normal")
    # Make sure overlay is on top of background but below text
    canvas.tag_raise(text_id)

# Start at full brightness (no overlay)
create_dimming_overlay(100)

# update Layout now, so that no errors occur, root.update() updates events too, we only want the idle tasks to be updated
root.update_idletasks()

### Bounce scrolling
_scroll_job = None
_scroll_direction = 1 # 1 -> down, -1 -> up

def stop_scroll():
    global _scroll_job
    if _scroll_job is not None:
        root.after_cancel(_scroll_job)
        _scroll_job = None
        
def start_bounce_scroll_if_needed():
    global _scroll_job, _scroll_direction
    
    stop_scroll()
    canvas.coords(text_id, w // 2, TOP_PAD)
    _scroll_direction = 1
    
    root.update_idletasks()
    bbox = canvas.bbox(text_id)	# (x1, y1, x2, y2)
    if not bbox:
        return
    x1, y1, x2, y2 = bbox
    visible_height = h - TOP_PAD
    text_height = y2 - y1
    
    # if it fits, don't scroll
    if text_height <= visible_height:
        return
    
    
    def tick():
        global _scroll_job, _scroll_direction
        
        bbox = canvas.bbox(text_id)
        if not bbox:
            return
        
        x1, y1, x2, y2 = bbox       
        
        # when scrolling down move the text up -> negative y
        if _scroll_direction == 1:
            #if bottom visible, pause and then reverse
            if y2 <= h:
                _scroll_direction = -1
                _scroll_job = root.after(SCROLL_DELAY_BOTTOM_MS, tick)
                return
            canvas.move(text_id, 0, -SCROLL_STEP_PX)
            _scroll_job = root.after(SCROLL_TICK_MS, tick)
            return
        
        # when scrolling back up move the text down
        if _scroll_direction == -1:
            #if bottom visible, pause and then reverse
            if y1 >= TOP_PAD:
                _scroll_direction = 1
                _scroll_job = root.after(SCROLL_DELAY_TOP_MS, tick)
                return
            canvas.move(text_id, 0, SCROLL_STEP_PX)
            _scroll_job = root.after(SCROLL_TICK_MS, tick)
            return        
    
    # Initial Pause at the top so the first lines are readable
    _scroll_job = root.after(SCROLL_DELAY_TOP_MS, tick)
    



### Tk event handler ###
def on_new_message_interrupt(event = None):  # Called if Flask sends -> "Interrupt Handler"
	global incoming_message
	
	print("[GUI] Updating text")
	
	canvas.itemconfigure(text_id, text = incoming_message)
	# Make sure text stays above brightness overlay
	canvas.tag_raise(text_id)
	start_bounce_scroll_if_needed()


def on_new_color_interrupt(event=None):
    """Called when Flask receives a color change request"""
    global incoming_color

    if incoming_color is None:
        return

    print(f"[GUI] Updating text color to: {incoming_color}")
    canvas.itemconfigure(text_id, fill=incoming_color)
    incoming_color = None


def on_new_brightness_interrupt(event=None):
    """Called when Flask receives a brightness change request"""
    global incoming_brightness

    if incoming_brightness is None:
        return

    print(f"[GUI] Updating brightness to: {incoming_brightness}%")
    create_dimming_overlay(incoming_brightness)
    incoming_brightness = None
	

text = tk.Text(
    root,
    bg = "black",
    fg = DEFAULT_TEXT_COLOR,
    bd = 0,
    highlightthickness = 0,
    wrap = "word"
)


def on_new_background_interrupt(event=None):
    global new_background_path, bg_image, bg_image_pil
    
    if new_background_path is None:
        return
    
    print(f"[GUI] Updating Background to: {new_background_path}")
    
    try:
        # load and resize new image
        bg_image_pil = Image.open(new_background_path).resize((w, h), Image.LANCZOS)
        bg_image = ImageTk.PhotoImage(bg_image_pil)
        
        # Update canvas with new image
        canvas.itemconfig(bg_image_id, image=bg_image)
        
        # Re-raise overlay and text above new background
        canvas.tag_raise(brightness_overlay_id)
        canvas.tag_raise(text_id)
        
        print("[GUI] Background Updated successfully")
    except HTTPException:
        raise
    except Exception as e:
        print(f"[GUI] Error updating background: {e}")
    finally:
        new_background_path = None

# Bind Events
root.bind('<<UpdateDisplay>>', on_new_message_interrupt)
root.bind('<<UpdateBackground>>', on_new_background_interrupt)
root.bind('<<UpdateColor>>', on_new_color_interrupt)
root.bind('<<UpdateBrightness>>', on_new_brightness_interrupt)

root.update_idletasks()


### MAIN ###
if __name__ == "__main__":
    try:
        # create a thread object (thread has not started yet)
        # daemon -> process that performs tasks in the background, server dies when thread stops
        # if daemon = False -> main thrad ends -> flask thread runs still -> python is alive -> ...
        t = threading.Thread(target = start_server, daemon = True) # offload the server into a thread
        t.start() #  server running in parallel in the background
        # start GUI
        root.mainloop()
        # Threads in python are not truly parallel, but it is not a problem as hhtp request -> server -> GUI
    except KeyboardInterrupt:
        print("\n[SERVER] Keyboard Interrupt received, shutting down cleanly")
    finally:
        print("[SERVER] Server stopped")


#=========== TO DO ============
# Tkinter is not thread safe, scheduling implemetieren
# Config block zur uebersicht
# Input Limit
# Clean Shutdown - Keyboard Interrupt does not work
# Logging instead of prints
# Display fonts
# If Image error, black background with a try/except block