NYU Scorm Cloud integration
===========================

This module integrates Sakai with [SCORM
Cloud](https://scorm.com/scorm-cloud/) via the Sakai Lessons tool.

To use a SCORM module in a site, an instructor adds a SCORM item to a
page in the Lessons tool.  They are prompted to upload a SCORM package
from their local machine and, behind the scenes, this package is sent
to the SCORM Cloud service where it is imported as a course.

Once the SCORM package has finished importing, the instructor can make
the new Lessons item available to students.  When a student accesses a
SCORM item for the first time, they are automatically registered with
the corresponding course in SCORM Cloud, and the SCORM player is
launched.  From here, they can work their way through the SCORM
module's content, including completing any quizzes that it contains.

When an instructor creates a SCORM item in Lessons, they have the
option of linking it to the Sakai Gradebook.  As students complete
quizzes in SCORM, their results are fetched from SCORM Cloud and
inserted in the Sakai Gradebook automatically.

What's here
-----------

This `scormcloud-service` module provides the backend of the SCORM
Cloud integration: the parts of the system that import SCORM Modules,
create student registrations, launch the SCORM player and integrate
with the Sakai Gradebook.

While the backend is relatively standalone, the UI parts of the code
are unfortunately quite entwined with the Lessons tool.  The following
commits relate to adding SCORM to Lessons (small mercy: most of the
code is in the first few commits, with the rest being small
adjustments):

  * https://github.com/NYUeServ/sakai11/commit/a060ae99d11fa955720b28f5714caa70f2ecddbb

  * https://github.com/NYUeServ/sakai11/commit/ce86acf440f61c328b70886c41eb689fdd877637

  * https://github.com/NYUeServ/sakai11/commit/ec79616a142e147e395c3850b354e3311b983371

  * https://github.com/NYUeServ/sakai11/commit/4598c38a3c5e333cb85b8938e788e9b4968475e6

  * https://github.com/NYUeServ/sakai11/commit/d880ac972e62c490d8fc0cd3a90ed5f3c0f877c0

  * https://github.com/NYUeServ/sakai11/commit/1eebce81b59f3524a9fd4017538ee3a1c6b7c745

  * https://github.com/NYUeServ/sakai11/commit/234a3409adedbb31d284e643a36d68e5da7ed20e

  * https://github.com/NYUeServ/sakai11/commit/b78f58dee1fd537c1bff2a5bed27c25514f5aa5e

  * https://github.com/NYUeServ/sakai11/commit/701cc9c805ec8a52a5c479fd30690a83cce7c1e9

  * https://github.com/NYUeServ/sakai11/commit/72b617f75769ef8a246452cd31fe9cd67b393b15

  * https://github.com/NYUeServ/sakai11/commit/39666d7f25d1fe190056ac0e03f11b275557a818

  * https://github.com/NYUeServ/sakai11/commit/9bf0e1ef6fecc8338ad40d7e0cf90f8b5a656d73

  * https://github.com/NYUeServ/sakai11/commit/7d0e62bb3b1775f3716772b44ab5761b9c7524ad

  * https://github.com/NYUeServ/sakai11/commit/602c0915410115321f5aa05d68b689983ff2e252
