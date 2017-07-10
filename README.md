LocalPlex
=========

This is a simple HTTP proxy for local services; it provides a unified interface
for them, switching based on the named host.

Allow me to back up.

I've got quite a few HTTP servers running on my machine--development
environments, backend libs, scripts generating html (that are then served
statically), etc.

Each of these run on their own local port...which means I have to remember quite
a few "http://localhost:8000" type addresses.  This is moderately annoying, and
bookmarks only work in a browser.

But `/etc/hosts` works everywhere.

Configuring
===========

Add lines to your `/etc/hosts` file with the following syntax:

    127.0.0.1 <memorable-name> # LocalPlex:http://localhost:<port>

The comment after the `#` signals that traffic to `<memorable-name>` should be
proxied to `localhost:<port>`.

So, for example, if you have a rails server running locally on port 3000, type
this:

    127.0.0.1 my-ticket-number.dev # LocalPlex:http://localhost:3000

If you also have a static HTTP server running on port 9999 (e.g. node's
`http-server -p 9999`), include this:

    127.0.0.1 demo-files # LocalPlex:http://localhost:9999

If, by chance, you've got a binary with a hardcoded endpoint that you want to
intercept & write a shim for, start your intercepting server on (e.g.) port 4500
and include this:

    127.0.0.1 replay-service-shim # LocalPlex:http://localhost:4500

Upon starting LocalPlex, it will grab these three entries (ignoring everything
else) and begin routing HTTP traffic to the specified endpoints.

(also, FWIW, `/etc/hosts` is only read once, at launch, so new edits to the file
will require a you to stop & restart the program)

Running in UserSpace
=====================

Clone the repo, and run:

    # sbt stage
    # PORT=1024 target/universal/stage/bin/localplex

This starts LocalPlex, listening on port 1024.

So now you can access the server `localhost:3000` from
`http://my-ticket-number.dev:1024` (whether in a browser, using curl, etc).  You
can access `localhost:4500` from `http://replay-service-shim:1024` as well.

Running with privileges
=======================

If you inexplicably trust code found on the internet, you can also run LocalPlex
against port 80 on your machine:

    # sudo PORT=80 target/universal/stage/bin/localplex

This gives you the opportunity to stop remembering ports altogether.  E.g.:

    # http -f POST http://my-ticket-number.dev form-value=whatever

Admin console
=============

You can access the standard finagle stats browser via `localhost:9990`...or, by
adding the following line to `/etc/hosts`:

    127.0.0.1 localplex-admin # LocalPlex:http://localhost:9990

Development server
==================

Use the revolver plugin while developing to restart the server on code changes:

    # sbt ~re-start

