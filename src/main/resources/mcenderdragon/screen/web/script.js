

function getConsoleOutput()
{
    var xhttp = new XMLHttpRequest(); 
    xhttp.onreadystatechange = function() {
        if (this.readyState === 4 && this.status === 200) {
          document.getElementById("console").innerHTML = this.responseText;
        }
      };
    xhttp.open("GET", "ajax_info.txt", true);
    xhttp.send();
}

var ansi_up = new AnsiUp;

function makeHTMLentries(txt)
{
    var html = ansi_up.ansi_to_html(txt);
    
    if(html.length === 0)
    {
        html = " ";
    }
    html = "<p class='line'>" + html + "</p>";
    return html;
}

function addLineToConsole(txt)
{
    var cdiv = document.getElementById("console");
    cdiv.innerHTML += makeHTMLentries(txt);
}



function addToConsole(txt)
{
    txt.split("\n").forEach(addLineToConsole);
} 

function preAutoscroll()
{
    var elements = document.getElementsByClassName("autoscroll");
    var downScolled = [];
    for(var i=0;i<elements.length;i++)
    {
        var elm = elements[i];
        if(elm.scrollTop + elm.offsetHeight >= elm.scrollHeight)
        {
            downScolled.push(elm);
        }
    }
    return downScolled;
}

function postAutoscroll(downScolled)
{
    downScolled.forEach(elm => elm.scrollTop = elm.scrollHeight);
}

function deleteServer(id) {
    const xhttp = new XMLHttpRequest();
    xhttp.open("POST", "data", true);
    xhttp.onreadystatechange = function()
    {
        if (this.readyState === 4 && this.status === 200)
        {
            updateServerList(JSON.parse(this.responseText));
        }
        else if(this.readyState === 4 && this.status === 403) {
            alert('Client is stil running and cannot be deleted.');
        }
    };
    xhttp.send("action=delete_inactive&server=" + id);
}

function updateServerList(serverList) {
    const activeEntry = getActiveEntry();
    const serversParent = document.getElementById("servers");

    const groups = {};

    serverList.forEach(e => {
        if(groups[e.group] === undefined) {
            groups[e.group] = [];
        }

        groups[e.group].push(e);
    })

    let output = '';
    let activeClientInstance = null;

    Object.keys(groups).sort((e1,e2) => e1.name < e2.name ? -1 : 1).forEach(group => {
        //Sort groups from A to Z
        groups[group] = groups[group].sort((e1,e2) => e1.name < e2.name ? -1 : 1);

        output += `<div class="groupTitle">${group}</div>`;

        output += groups[group].map(e => {
            let active = e.id === activeEntry;

            if(active) {
                activeClientInstance = e;
            }

            return `<div class="entry ${active ? "active" : ""}">
                    <a href="index.html?server=${e.id}">
                        <div class="name">
                            ${e.name}
                        </div>
                    </a>
                    <div class="running_${e.running}">
                        Running: ${e.running}
                    </div>
                    ${e.running ? '' : `
                        <button onclick="deleteServer('${e.id}')">
                            Delete
                        </button>
                    `}
                </div>`;
        }).join('\n');

    })

    serversParent.innerHTML = output;

    if(activeClientInstance !== null) {
        let animation = document.getElementById("loading_animation");
        if(activeClientInstance.running)
        {
            animation.style = "";
        }
        else
        {
            animation.style.display="none";
            clearInterval(refreshId);
        }
    }
}

function retriveServerList()
{
    const xhttp = new XMLHttpRequest();
    xhttp.open("POST", "data", true);
    xhttp.onreadystatechange = function() 
    {
        if (this.readyState === 4 && this.status === 200)
        {
            updateServerList(JSON.parse(this.responseText));
        } 
    };
    xhttp.send("action=get_clients");
}

function getActiveEntry()
{
    const urlSearchParams = new URL(window.location.href).searchParams;
    if(urlSearchParams.has("server"))
    {
        return urlSearchParams.get("server");
    }
    else
    {
        return undefined;
    }
}

var prepareTextAsync = function(lines) {
    return new Promise(resolve => {
        var rawHTML = "";
        lines.forEach(e => {
            if(lastLine < e.line)
            {
                lastLine = e.line;
                rawHTML += makeHTMLentries(e.text);
            }
        });
        resolve(rawHTML);
    });
}; 


var lastLine = -1;

function retriveConsoleEntries(lineStart)
{
    var selected = getActiveEntry();
    if(selected !== undefined)
    {
        var xhttp = new XMLHttpRequest(); 
        xhttp.open("POST", "data", true);
        xhttp.onreadystatechange = async function() 
        {
            if (this.readyState === 4 && this.status === 200)
            {
                var lines = JSON.parse(this.responseText);
                   
                var downScolled = preAutoscroll(); 

                var emptyDiv = document.getElementById("console").appendChild(document.createElement("div"));

                prepareTextAsync(lines).then((rawHTML) => emptyDiv.innerHTML = rawHTML).then((dat) => postAutoscroll(downScolled));
            } 
        };
        xhttp.send("action=get_log&server=" + selected + "&line_start="+lineStart);
    }
}

function sendToServer()
{
    var text = document.getElementById("text_send").value;
    document.getElementById("text_send").value = "";
    var selected = getActiveEntry();
    var xhttp = new XMLHttpRequest(); 
        xhttp.open("POST", "data", true);
        xhttp.onreadystatechange = function() 
        {
            if (this.readyState == 4 && this.status != 200) 
            {
                alert(this.responseText); 
            } 
        };
        xhttp.send("action=send_command&server=" + selected + "&command="+text);

}

function getLastLine()
{
    return lastLine;
}


 

var txt  = "\n\n\033[1;33;40m 33;40  \033[1;33;41m 33;41  \033[1;33;42m 33;42  \033[1;33;43m 33;43  \033[1;33;44m 33;44  \033[1;33;45m 33;45  \033[1;33;46m 33;46  \033[1m\033[0\n\n\033[1;33;42m >> Tests OK\n\n";
/*setInterval(function()
{
    var downScolled = preAutoscroll();    
    addToConsole(txt);
    postAutoscroll(downScolled);
   
} , 3000);*/

document.getElementById("but_send").onclick = sendToServer;
document.getElementById("text_send").addEventListener("keyup", function(event)
{
    if(event.keyCode == 13) //Enter
    {
        event.preventDefault();
        sendToServer();
    }
});

retriveServerList();
var refreshServersId = setInterval(retriveServerList, 5000);

retriveConsoleEntries(getLastLine());
var refreshId = setInterval(function() 
{
    retriveConsoleEntries(getLastLine());
}, 1000);