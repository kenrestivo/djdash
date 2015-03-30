# DJ Dashboard

A Dashboard for a heavily-customized online Liquidsoap/Airtime radio station, [SPAZ radio](http://spaz.org/radio). It shows buffer status, listener counts, and allows chatting with listneners. The listeners chat using a different user interface.

## Usage

When the buffer goes to zero, the DJ's transmission has dropped out and the listeners will hear several seconds of the jukebox instead. Good buffer status is essential to a good streaming transmission. Ideally it is a nice straight line. If the buffer is choppy or low, or has dropouts, the DJ needs to increase their available bandwidth, and/or reduce load on the computer doing the streaming.


## Building

```shell
lein cljsbuild once
rm -rf target/trampolines/
lein with-profile user,repl trampoline repl :headless < /dev/null &
```

## Why???

Because the DJ's like to chat with the listeners. And they like to see whether their connection has dropped out, and what's the status of the buffer. And they like to see a running count of listeners.

## Future

More features coming, including showing the schedule, digital audio peak meter, etc. I also will bust out some of the features separately to be used by other radio stations.

## License

Copyright © 2014-2015 ken restivo <ken @ restivo.org>

Based in part on the [binary clock app](https://github.com/fredyr/binclock) tutorial.

[CSS for LEDs from here](https://github.com/aus/led.css).

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
