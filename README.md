### Git Migrations

Git-migrations is a simple database migration system aimed at exploiting the distributed, branchable
nature of the [Git version control tool](http://git-scm.com/).

Currently the "project" is a fairly bare bones as I explore the potential of using feature branches,
and the disparate, distributed nature of git for complex migration needs and shouldn't be considered
production worthy/ready.

A short YouTube video showing the basic idioms being explored can be seen at:

  http://www.youtube.com/watch?v=dElKSWf9n24

When build using ``sbt one-jar`` you can put the ./git-migrate script on your PATH to run the application.

Check out https://github.com/talios/git-migration-sample for the sample database migration repository used
in the video.
