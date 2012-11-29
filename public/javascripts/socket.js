$ = jQuery.noConflict();
humane.error = humane.spawn({ addnCls: 'humane-libnotify-error'});

var _socket = {
  init:function(){
    var WSurl = document.location.href.replace("http", "ws")+"ws";

    if(getMyName() != "") WSurl += "?userId="+getMyName();

    socket = new WebSocket(WSurl);
    socket.onopen = function(){
      console.log("Socket has been opened!");
    }

    socket.onmessage = function(msg){
      var d = JSON.parse(msg.data);
      console.log(d);
      if(d.error) {
        console.log("error, closing socket...", d.error);
        socket.close();
        humane.error("Socket error");
      }
      else
        _socket.handleMessage(d);
    }
    socket.onerror = function(err) {
      console.log("Socket error ",err);
      humane.error("Socket error ",err)
    }
  },
  sendData : function(kind, data) {
    var _data = JSON.stringify(data);
    socket.send(_data);
  },
  handleMessage : function(d) {
    console.log(d);
    if(d.kind=="youAre")
      setMyName(d.pid);
    else if(d.kind=="newPlayer")
      createPlayer(d.c);
    else if(d.kind=="newDirection") {
      Crafty.trigger("newDirection"+d.c.userId, d.c);
    }
    else if(d.kind=="position") {
      Crafty.trigger("position"+d.c.userId, d.c);
    }
  }
};


var players = {};
var pname = "";
function promptForName() {
  var name = prompt("Enter your name");
  setMyName(name);
  return getMyName();
}
function setMyName(name) {
  pname=name;
}
function getMyName() {
  if(pname != "") return pname;
  else return promptForName();
}

$(function(){
  promptForName();
  _socket.init();
})