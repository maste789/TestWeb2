/*var socketIO = require('socket.io');
var server = require('http').createServer().listen(7000, '0.0.0.0');
var io = socketIO.listen(server);*/

var express = require('express');
var app = express();

var server = require('http').createServer().listen(3000 , '192.168.0.38');
var io = require('socket.io')(server);

app.get('/',function(req,res){
    res.sendfile("client.html");
})


server.listen('3000',function(){
console.log("start socket 8888")
});

// Super simple server:
//  * One room only.
//  * We expect two people max.
//  * No error handling.

io.sockets.on('connection', function (client) {
    console.log('new connection: ' + client.id);
    client.on('disconnect',function(){
    console.log('user disconnected');
    })
    client.on('offer', function (details) {
        client.broadcast.emit('offer', details);
        console.log('offer: ' + JSON.stringify(details));
    });

    client.on('answer', function (details) {
        client.broadcast.emit('answer', details);
        console.log('answer: ' + JSON.stringify(details));
    });
    
    client.on('candidate', function (details) {
        client.broadcast.emit('candidate', details);
        console.log('candidate: ' + JSON.stringify(details));
    });
    //client.on("Messege",(text) =>
    // Here starts evertyhing!
    // The first connection doesn't send anything (no other clients)
    // Second connection emits the message to start the SDP negotation
    client.broadcast.emit('createoffer', {});
});

