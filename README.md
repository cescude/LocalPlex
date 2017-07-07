LocalPlex
=========

This is a simple HTTP proxy for local services; it provides a unified interface
for them, switching the named Host.

Allow me to back up.

I've got quite a few HTTP servers running on my machine--development
environments, backend libs, scripts generating html (that's then served
statically), etc.  

Each of these run on their own local port, which means I have to remember quite
a few "http://localhost:8000" type addresses.  This is moderately annoying, and
bookmarks only work in a browser.  

But /etc/hosts works everywhere.

Configuring
===========

Add lines to your /etc/hosts file with the following syntax:

    127.0.0.1 <memorable-name> # LocalPlex:localhost:<port>

So, for example, if you have a rails server running locally on port 3000, type
this:

    127.0.0.1 my-ticket-number.dev # LocalPlex:localhost:3000

If you also have a static HTTP server running on port 9999 (e.g. node's
`http-server -p 9999`), include this:

    127.0.0.1 demo-files # LocalPlex:localhost:9999

If, by chance, you've got a binary with a hardcoded endpoint that you want to
intercept & write a shim for, start your shim (on port 4500) and include this:

    127.0.0.1 replay-service-shim # LocalPlex:localhost:4500

Upon starting LocalPlex, it will grab these three entries (ignoring everything
else) and setup Host based proxying to the specified endpoints.

If you update /etc/hosts, you _will_ need to restart LocalPlex.

Running in UserSpace
=====================

Clone the repo, run:

    # sbt stage
    # target/universal/stage/bin/localplex

This starts LocalPlex, which (by default) listens on port 1024.

So now you can access the server `localhost:3000` from
`http://my-ticket-number.dev:1024` (whether in a browser, using curl, etc).  You can
access `localhost:4500` from `http://replay-service-shim:1024` as well.

Running with privileges
=======================

If you trust code found on the internet, you can run this as:

    # sudo PORT=80 target/universal/stage/bin/localplex

Which will bind LocalPlex to the standard http port, allowing you to stop
remembering specific ports altogether.  E.g.:

    # http -f POST http://my-ticket-number.dev form-value=whatever

Admin console
=============

You can access the standard finagle stats browser via `localhost:9990`...or, by
adding the following line to your /etc/hosts.

    127.0.0.1 localplex-admin # LocalPlex:localhost:9990



