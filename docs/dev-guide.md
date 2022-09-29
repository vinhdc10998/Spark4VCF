# Intro to Development on vep-spark

This document outlines best practices and requirements to follow for developers
working on vep-spark project, including topics from general development guidelines,
to our standard git workflows, to repository structure, and more.

## General Development Philosophy

We summarize best development practices with three central tenets (in no
particular order!).

* Make your fellow developers happy.
* Make your fellow devops happy.
* Make your users happy.

There are many aspects that go into each of these. Some fall under multiple or
all categories:

* Unit test (untested code is broken code).
* Use the most efficient algorithms and datastructures in implementation.

### Making Devs Happy

* Name things clearly (not longer than necessary, but long enough to be clear
  and readable).
* Document things that might not be clear to someone else (or to you, 3 months
  from now).
    * For documentation aim to explain not *what* (things like what a function
      is supposed to output—which should hopefully be obvious from your code)
      but *why* and *how*: how is this function used elsewhere, why are any
      idiosyncracies necessary, how does it interface with the rest of the code?
* Modularize separate functionalities and aim to write reusable code.
* Follow the applicable style guide.
* Make code simple to extend and refactor.

### Making DevOps Happy

* Test deploying your code in your dev namespace, and make sure it's functional
  end-to-end before asking for review.
* Avoid making breaking changes unless absolutely necessary (to avoid headaches
  with configuring new deployments).

### Making Users Happy

Perhaps the most complicated of the three tenets, but ultimately the most
important. Users vary in intent and level of technical competence: some will
only ever use the simplest portal features, some will be versed in using GraphQL
for querying data, some will never touch the portal and work directly with API
calls.

* Strive to make user interfaces intuitive, whether in frontend design or API
  endpoints and general API design.
* For APIs in particular, if a user will use it, include documentation somewhere
  that explains how to use it. More generally: if *anyone* will use it—explain
  how to use it.

## GitHub Usage

It's helpful to add a global `.gitignore` file with typical entries, such as
`.DS_Store` on OS X, or `*.swp` files for vim users.

### Naming Conventions

Branches are named as `type/scope`, and commit messages are written as
`type(scope): explanation`, where

* `scope` identifies the thing that was added or modified,
* `explanation` is a brief description of the changes in imperative present
  tense (such as "add function to \_", not "added function"),
* and `type` is defined as:

```
type = "chore" | "docs" | "feat" | "fix" | "refactor" | "style" | "test"
```

Some example branch names:

* `refactor/db-calls`
* `test/user`
* `docs/deployment`

Some example commit messages:

* `fix(scope): remove admin scope from client`
* `feat(project_members): list all members given project`
* `docs(generation): fix generation script and update docs`

### Pull Requests (PRs)

**Before submitting a PR for review, try to make sure you've accomplished these things:**

* The PR:
    * contains a brief description of what it changes and/or adds,
    * passes status checks,
    * maintains or increases code coverage,
    * and follows the style guidelines for applicable languages.
* If the PR implements a bug fix, it includes regression tests.
* The PR addresses a specific issue, and not multiple issues at once.

**To merge the PR:**

* If the branch now has conflicts with the master branch, follow these steps to
  update it:

```
git checkout master
git pull origin master
git checkout $YOUR_BRANCH_NAME
git merge master
git commit
# The previous command should open an editor with the default merge commit
# message; simply save and exit
git push
```

* Merge using the default merge strategy (not squash or rebase). If you have
  many small or unnecessary commits, use `git rebase` to squash these before
  merging. For example, `git rebase -i HEAD~5` will allow you to select commits
  from the previous 5 commits to squash.

**After merging the PR:**

* Delete the branch from GitHub.
* If necessary, create a new release version (click the `Releases` tab in the
  main GitHub page).

## Logging

### Log Level

Logging serves three main purposes: security compliance, incident management,
and debugging. Depending on the purpose of the logs, choose which log level to
use and decide how to structure logs to make them useful for that purpose.

#### ERROR

> *Something is  wrong and it breaks the system; this needs to be addressed by an administrator now.*

Examples:

* Failure to communicate with database.
* Failure to communicate with another service.

Expected and handled failures should NOT log as `ERROR` to avoid creating noise.
As much as possible reserve `ERROR`-level logs to assist in diagnosing major
problems.

Note that in Python, `log.exception` has this same log level as `log.error`. Use
`log.exception` when the traceback is valuable to diagnose the problem.

An example of correct usage of `log.exception` would be in a Flask API error
handler: after it handles all expected errors, it should also handle any general
`Exception`; in this case we need to use `log.exception` to have the traceback
available to see what’s going on.

#### WARN

> *Something might be wrong and likely needs to be addressed soon.*

Alternatively:

> *Execution encountered an exceptional case which can be handled but leads to
> undesirable behavior.*

#### INFO

> *Some action needs to be logged as an important state change or for security compliance.*

Examples:

* Created/deleted a user.
* Started/finished database migration.

Avoid overly verbose logging at the `INFO` level, to avoid creating unnecessary
noise in prod logs.

#### DEBUG

> *Statement useful for debugging purposes only.*

Examples:

* Attempting to connect to an external API.

`DEBUG` level logs should be sufficiently informative for a developer to have a
clear picture of what's going on when reproducing a bug, just looking at the
logs.

The value of debug statements may change over time as the software evolves; we
need to routinely review our current logging to remove noise and add more useful
traces.

### Log Message Structure

The log message should form an entire sentence understandable by a developer
who didn't write the relevant code, and provide additional context to the
message beyond (for example) "failed to do X".

In detail, using a generic error as an example, the log message should try to
provide information about why this action was attempted, what caused the
failure, how (if at all) it is handled, and what consequences this will have.

## Python

For getting set up for general Python development, install [virtualenv][] and
[Pipenv][]. Set up a separate virtual environment for every package you work
on to avoid dependency conflicts, you may also want to install [pyenv][] and
[pyenv-virtualenv][] or [virtualenvwrapper][] according to personal taste.

### Repository Setup

To create a new repository, install [Cookiecutter][] and run:

```
cookiecutter gh:uc-cdis/template-repo
```

### Style

For Python code we use [black][] to automatically apply formatting. Install a
plugin for your editor of choice or run it on code before committing. For
details on python style see the [python style guide][python-style-guide].

### Dependencies

Python repositories must contain a `setup.py` file, and generally also contain a
`Pipfile` and `Pipfile.lock`. `Pipfile.lock` is a lock file, containing the exact
versions for all packages installed in a working build, generated by [Pipenv][].
`Pipfile` and `setup.py` depend on the type of the package.

In general there are two types of packages: **applications** and **libraries**.
Application is usually the one deployed and executed directly from source, without
being depended by others; while a library is always imported by applications or
other libraries.

For applications, **all** dependencies go into `Pipfile`, and dependencies for
development e.g. tests should be placed in the dev section. Depending on actual
use case, versions in `Pipfile` may or may not be pinned, because `Pipfile.lock`
eventually defines the idempotent environment.

For libraries, runtime dependencies go into `setup.py`, development dependencies
go into the dev section of `Pipfile`. In addition, `Pipfile` also includes the
runtime dependencies through an editable source-installation link like this:

```
package_name = {editable = true,path = "."}
```

Versions must not be pinned in `setup.py`, however you may specify ranges:

```
"requests>=2.19.1,<3.0.0"
```

With above rules, for both applications and libraries, you may start development
with the same command:

```
pipenv install --dev
```

And deploying applications should always be:

```
pipenv install --deploy
```
