
var spaz_radio_chat = (function (options) {

    var settings = {'port':  Number(1884),
		    'timeout': 10000};

    $.extend(settings, options);


    if ("WebSocket" in window){
	console.log("good, websockets supported, we can chat");
    } else {
	settings.onMessage({'user': 'Your browser',
			    'message': 'Sorry, your browser is too old to support Websockets, so no chat for you :('});
	return {};
    }

    var presence_chan = 'presence/#';

    var fakeid = 'mut' + generateUIDNotMoreThan1million();
    var username = "";

    var conn_status = 0;

    var users = new Object();

    function cheap_log(x){
	console.log(x);
    }
    

    function now(){
	return (new Date()).toString();
    }


    function unique_users(){
	return $.unique($.map(users, function(val, key) { return val; })).sort();
    }

    var conn = new Paho.MQTT.Client(settings.serv, settings.port, fakeid);

    function subscribe(){
	conn.subscribe(settings.chan, {'qos': 2});
    }

    function subscribe_presence(){
	conn.subscribe(presence_chan, {'qos': 2});
    }


    function send_presence(n){
	var x;
	var topic;
	if(n > ""){
	    x = n;
	} else{
	    x = "";
	}
	var msg = new Paho.MQTT.Message(x);
	msg.destinationName = 'presence/' + fakeid;
	msg.qos = 2;
	msg.retained = true;
	conn.send(msg);
    }


    function send(text){
	var msg = new Paho.MQTT.Message(JSON.stringify({'user': username,
							'message': text}));
	msg.destinationName = settings.chan;
	msg.qos = 2;
	conn.send(msg);
    }




    function send_hello(){
	send("yo! " + now());
    }


    function update_roster(){
   	if(typeof settings.onRosterChanged === "function"){
	    try {
		settings.onRosterChanged(unique_users());
	    } catch(err) {
		console.log(err);
	    }
	}

    }

    function handle_message(msg){
	// console.log(msg);
   	if(typeof settings.onMessage === "function"){
	    try {
		settings.onMessage(msg);
	    } catch(err) {
		console.log(err);
	    }
	}
    }


    function on_message(m){
	if(m.destinationName == settings.chan){
	    try {
		var msg = JSON.parse(m.payloadString);
		handle_message(msg);
	    } catch(err) {
		console.log(err);
	    }
	    return;
	}

	/// presence
	//console.log("presence: " + m.destinationName + " is: " + m.payloadString);

	var u = m.payloadString;
	var c =  m.destinationName.split('/')[1];

	if(u > ""){
	    users[c] = u;
	} else {
	    // it's a delete user message (blank username)
	    delete users[c];
	}

	update_roster();
    }


    function conn_changed(n){
	var prev = conn_status;
	conn_status = n;
	//console.log("connection " + conn_status + " -> " + n);

	if(conn_status > 0 && prev < 1){
   	    if(typeof settings.onConnected === "function"){
		try {
		    settings.onConnected(n);
		} catch(err) {
		    console.log(err);
		}
	    }
	}

	if(prev > 0 && conn_status < 1){
   	    if(typeof settings.onDisconnected === "function"){
		try {
		    settings.onDisconnected(n);
		} catch(err) {
		    console.log(err);
		}
	    }
	}

    }

    function gbcw(){
	var msg = new Paho.MQTT.Message(""); // clear it out!
	msg.destinationName = 'presence/' + fakeid;
	msg.qos = 2;
	msg.retained = true;
	return msg;
    }


    function change_nick(n){
	username = n;
	// first log them out!
	if(conn_status > 0){
	    send_presence("");
	    send_presence(username);
	}
    }


    function fetch_history(){
	$.ajax({
            'url': settings.history_url,
	    'contentType': "application/json",
	    'dataType': 'jsonp',
	    'crossDomain': true,
	    'type': 'GET',
            'success': function (r) {
		try {
		    if (r.status === "OK") {
			for (i = 0; i < r.history.length; ++i) {
			    handle_message(r.history[i]);
			}
		    } else {
			console.log(r);
			handle_message({'user': 'Chat', 'message': 'Network error reaching chat server'});
			setTimeout(fetch_history, settings.timeout); // and hopefully don't blow the stack?
		    }
		} catch(err) {
		    console.log(err);
		}},
	    'async': false
	});
    }

    conn.onConnectionLost = function(x){console.log(x); conn_changed(0);};
    conn.onMessageArrived = on_message;

    function connect(){
	if(conn_status < 1){
	    console.log("chat connecting");
	    try {
		conn.connect({'keepAliveInterval': 30,
			      'willMessage': gbcw(),
			      'onSuccess': 
			      function(x){
				  cheap_log("chat connected!") ;
				  conn_changed(1);
				  fetch_history();
				  subscribe();
				  update_roster();
				  subscribe_presence();
			      },
			      'onFailure': function(x){console.log(x); conn_changed(0);}});
	    } catch(err){
		console.log(err);
	    }
	} else {
	    if((username > "") && ($.inArray(username, unique_users()) < 0)){
		send_presence(username);
	    }
	}
	setTimeout(connect, settings.timeout);	
    }


    connect();



    $(window).bind('beforeunload', function(){
	// important, otherwise they stay logged in forever.
	send_presence(0);
	return "";
    });


    return {'conn': conn,
	    'login': change_nick,
	    'sendMessage': send};
});

