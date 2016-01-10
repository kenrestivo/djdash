# DJ Dashboard

A Dashboard for a heavily-customized online Liquidsoap/Airtime radio station, [SPAZ radio](http://spaz.org/radio). It shows buffer status, listener counts, and allows chatting with listeners. Listeners access the same chat via a different user interface.

## Configuration

Example configs are in resources/configs.

Config files are in EDN format. The file formet is lightly validated at load time, mostly just to check that required keys are present and the types of values are correct.

To use the buffer monitoring, you have to enable the "logfile" directive on the liquidsoap harbor, and point it to the same file that (-> :tailer :fpath) is in the env.

## Building

```shell
lein uberjar
```
I run this from a uberjar as an unprivileged user from an init script, an example of which is in resources/scripts/djdash-init. There's also an upstart file in resources/scripts/djdash.conf

## Usage

```sh
	target/djdash.jar config.edn
```


### Connection Quality
When the buffer goes to zero, the DJ's transmission has dropped out and the listeners will hear several seconds of the jukebox instead. Good buffer status is essential to a good streaming transmission. Ideally it is a nice straight line. If the buffer is choppy or low, or has dropouts, the DJ needs to increase their available bandwidth, and/or reduce load on the computer doing the streaming.

### Listeners
Shows the listener count. The server pulls this off of Icecast, and updates in near-real-time to the dashboard via pub/sub, and to the main SPAZ site via JSON file.

### Now Playing
Also pulled from Icecast, and munged and updated in near-real-time to the dashboard via pub/sub. Also pushed to file for the main SPAZ site to use.

### Listener location
DIsplays a dynamically-updated geo-encoded map of listeners location, based on IP address.

### Upcoming shows and now scheduled
Displays what show is currently scheduled and the next shows scheduled. This is pulled from Airtime APIs, and massively transformed in order to be localizable on the client. An endpoint provides an HTML-ifed schedule localized into the local timezone.

### Chat
An alternate UI to the main SPAZ radio chat, so that DJ's can chat while monitoring the other things simultaneously, in one window. The chat uses MQTT as a backend, to conserve bandwidth on mobile networks.

## Why???

Because the DJ's like to chat with the listeners. And they like to see whether their connection has dropped out, and what's the status of the buffer. And they like to see a running count of listeners. And where they're located.

## Future

More features coming, including showing a digital audio peak meter, etc. I also will bust out some of the features separately to be used by other radio stations.

## License

Copyright © 2014-2015 ken restivo <ken @ restivo.org>

Originally inspired by the [binary clock app](https://github.com/fredyr/binclock) tutorial.

[CSS for LEDs from here](https://github.com/aus/led.css).

Maps via [MapQuest](http://www.mapquest.com/), via [OpenStreetMap](http://www.openstreetmap.org/), using [Leaflet](http://leafletjs.com/).

Graphs via [Flot](http://www.flotcharts.org/).

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
