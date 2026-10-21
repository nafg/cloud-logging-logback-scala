inThisBuild(
  List(
    homepage                            := Some(uri("https://github.com/nafg/cloud-logging-logback-scala")),
    licenses                            := List("Apache-2.0" -> uri("https://www.apache.org/licenses/LICENSE-2.0")),
    developers                          := List(
      Developer("nafg", "Naftoli Gugenheim", "98384+nafg@users.noreply.github.com", uri("https://github.com/nafg"))
    ),
    dynverGitDescribeOutput             :=
      dynverGitDescribeOutput.value.map { o =>
        o.copy(dirtySuffix = o.dirtySuffix.value match {
          case "" => o.dirtySuffix
          case _  => sbtdynver.GitDirtySuffix("_")
        })
      },
    dynverSonatypeSnapshots             := true,
    githubWorkflowJavaVersions          := Seq(JavaSpec.zulu("17")),
    githubWorkflowScalaVersions         := githubWorkflowScalaVersions.value.map(_.replaceFirst("\\d+$", "x")),
    // sbt 2's `test` is incremental (testQuick); with the sbt disk cache CI restores, it can run no tests
    githubWorkflowBuild                 := Seq(WorkflowStep.Sbt(List("testFull"), name = Some("Build project"))),
    githubWorkflowTargetTags ++= Seq("v*"),
    githubWorkflowPublishTargetBranches := Seq(RefPredicate.StartsWith(Ref.Tag("v"))),
    githubWorkflowPublish               := Seq(
      WorkflowStep.Sbt(
        List("ci-release"),
        env = Map(
          "PGP_PASSPHRASE"    -> "${{ secrets.PGP_PASSPHRASE }}",
          "PGP_SECRET"        -> "${{ secrets.PGP_SECRET }}",
          "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
          "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
        )
      )
    )
  )
)
