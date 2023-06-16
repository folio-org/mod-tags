# mod-tags

Copyright (C) 2018-2023 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Introduction

Central list of tags that can be assigned to various objects. Tags are simple
strings like "urgent", or "important". These are kept as an array of string values
in the various objects that use them. This module provides a centralized repository
of allowed values, which can be used for populating pull-down lists etc. The coupling
is intentionally rather weak, it will be up to the UI to enforce the use of
authorized tags, or not, as each library will want, and/or insert new tags into
this module when the user uses a new one.

## Additional information

### Issue tracker

See project [MODTAG](https://issues.folio.org/browse/MODTAG)
at the [FOLIO issue tracker](https://dev.folio.org/guidelines/issue-tracker).

### ModuleDescriptor

See the built `target/ModuleDescriptor.json` for the interfaces that this module
requires and provides, the permissions, and the additional module metadata.

### Other documentation

Other [modules](https://dev.folio.org/source-code/#server-side) are described,
with further FOLIO Developer documentation at [dev.folio.org](https://dev.folio.org/)

### API documentation

This module's [API documentation](https://dev.folio.org/reference/api/#mod-tags).

### Code analysis

[SonarQube analysis](https://sonarcloud.io/dashboard?id=org.folio%3Amod-tags).

### Download and configuration

The built artifacts for this module are available.
See [configuration](https://dev.folio.org/download/artifacts) for repository access,
and the [Docker image](https://hub.docker.com/r/folioorg/mod-tags/).

