import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.BuildFailureOnMetric
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.failOnMetricChange
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import jetbrains.buildServer.configs.kotlin.v2019_2.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

/*
version = "2020.1"

project {

    buildType(Build)
}

object Build : BuildType({
    name = "Build"

    vcs {
        root(DslContext.settingsRoot)
    }

    triggers {
        vcs {
        }
    }
})*/

version = "2020.1"

project {

    buildType(ProductionBuild)
    buildType(Deploy)
}

object Deploy : BuildType({
    name = "Deploy"

    enablePersonalBuilds = false
    type = BuildTypeSettings.Type.DEPLOYMENT
    maxRunningBuilds = 1

/*    params {
        password("AWSAccessKeyId", "******", display = ParameterDisplay.HIDDEN)
        password("AWSSecretAccessKey", "******", display = ParameterDisplay.HIDDEN)
    }*/

    vcs {
        checkoutMode = CheckoutMode.MANUAL
    }

    steps {
        script {
            name = "Deploy to S3"
            scriptContent = """
                export AWS_ACCESS_KEY_ID=%AWSAccessKeyId%
                export AWS_SECRET_ACCESS_KEY=%AWSSecretAccessKey%
                export AWS_DEFAULT_REGION=us-east-1
                
                aws s3 sync build/ s3://danielgallo-teamcity/
            """.trimIndent()
            dockerImage = "amazon/aws-cli:latest"
        }
    }

    dependencies {
        artifacts(ProductionBuild) {
            buildRule = lastSuccessful()
            artifactRules = "MyReactApp.zip!** => build/"
        }
    }

/*    requirements {
        equals("system.agent.name", "Windows Server 2019 (AWS)")
    }*/
})

object ProductionBuild : BuildType({
    name = "Production Build"

    artifactRules = "build => MyReactApp.zip"

    vcs {
        root(DslContext.settingsRoot)
    }

    steps {
        script {
            name = "npm install"
            scriptContent = """
                npm install
            """.trimIndent()
        }
        script {
            name = "npm build"
            scriptContent = """
                npm run build
            """.trimIndent()
        }
        script {
            name = "npm test"
            scriptContent = """
                set CI=true && npm run test-ci
            """.trimIndent()
        }
    }

    triggers {
        vcs {
        }
    }

    failureConditions {
        failOnMetricChange {
            metric = BuildFailureOnMetric.MetricType.ARTIFACT_SIZE
            threshold = 1000
            units = BuildFailureOnMetric.MetricUnit.DEFAULT_UNIT
            comparison = BuildFailureOnMetric.MetricComparison.LESS
            compareTo = value()
        }
    }

/*    requirements {
        equals("system.agent.name", "Windows Server 2019 (AWS)")
    }*/
})
