## Startoptions
### screen.webservice.port
Default: 8087

Used to change the Port were the webside is running

### screen.port
Default: 9153

Port were the screen server is running. Als screen client will connect to this port and communicate via it with the server

### screen.webservice.need_authentication
Default: false

If set to true you need Username and Password to access the site. 

### screen.webservice.auth_file
Default: users.txt

Name of the file used for authentification. Per line there is a `user=password` entry, the Password must be a SHA256 hash in Hex numbers.

### application.name
Default: the command used to run this program

This is the Name of the program which will be used for displaying.