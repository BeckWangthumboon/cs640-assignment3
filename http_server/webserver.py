from http.server import SimpleHTTPRequestHandler
from socketserver import TCPServer

class CS144Handler(SimpleHTTPRequestHandler):

    # Disable logging DNS lookups
    def address_string(self):
        return str(self.client_address[0])


PORT = 80

Handler = CS144Handler
httpd = TCPServer(("", PORT), Handler)
print("httpd serving at port", PORT)
httpd.serve_forever()
