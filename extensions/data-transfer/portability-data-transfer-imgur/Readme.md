# Imgur
This folder contains the extension implementation for the
[Imgur](https://imgur.com/) service.


## Data Supported

  - Albums Import & Export
  - Photos Import & Export

## Current State
No known issues, however like much of DTP still needs some in depth testing to ensure corner
cases are working.

## Keys & Auth

Apply for a key at [https://api.imgur.com/oauth2/addclient](https://api.imgur.com/oauth2/addclient)

Your callback needs to be set to `https://<yourdomain>/callback/imgur`, even if that domain is `localhost`

Imgur uses OAuth 2.0 for authorization.

## Maintained By

The Imgur extension was created by the
[DTP maintainers](mailto:portability-maintainers@googlegroups.com)
and is not an official product of Imgur.
