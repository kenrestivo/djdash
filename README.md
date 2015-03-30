# DJ Dashboard

A Dashboard for a heavily-customized online Liquidsoap/Airtime radio station, [SPAZ radio](http://spaz.org/radio). It shows buffer status, listener counts, and allows chatting with listneners. The listener chat uses a different user interface.

## Configuration

Most of the settings are in the project.clj using environ. They can be overridden using environ too. Eventually I'll move those settings to a separate conf file.

To use the buffer monitoring, you have to enable the "logfile" directive on the liquidsoap harbor, and point it to the same file that (-> :tailer :fpath) is in the env.

## Building

```shell
lein cljsbuild once
rm -rf target/trampolines/
lein with-profile user,repl trampoline repl :headless < /dev/null &
```

## Usage

### Buffer
When the buffer goes to zero, the DJ's transmission has dropped out and the listeners will hear several seconds of the jukebox instead. Good buffer status is essential to a good streaming transmission. Ideally it is a nice straight line. If the buffer is choppy or low, or has dropouts, the DJ needs to increase their available bandwidth, and/or reduce load on the computer doing the streaming.

### Listeners
Shows the listener count. Listener counts are calculated by some hairy custom code on the streaming server, which will eventually be rewritten nicely and folded into this dashboard.

### Now Playing
Again, generated from some hairy code on the server. It too will eventually find its way here.

### Chat
An alternate UI to the main SPAZ radio chat, so that DJ's can chat while monitoring the other things simultaneously, in one window.

## Why???

Because the DJ's like to chat with the listeners. And they like to see whether their connection has dropped out, and what's the status of the buffer. And they like to see a running count of listeners.

## Future

More features coming, including showing the schedule, digital audio peak meter, etc. I also will bust out some of the features separately to be used by other radio stations.

## License

Copyright © 2014-2015 ken restivo <ken @ restivo.org>

Based in part on the [binary clock app](https://github.com/fredyr/binclock) tutorial.

[CSS for LEDs from here](https://github.com/aus/led.css).

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
