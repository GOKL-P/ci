package LinuxBuild_CommonServicesAndTools_CoreLibraries.buildTypes

import LinuxBuild.vcsRoots.LinuxBuild_Lens7vcs
import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.exec
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.BuildFailureOnText
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.failOnText
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.finishBuildTrigger
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs

object LinuxBuild_CommonServicesAndTools_CoreLibraries_BuildCoreLibrariesMetered : BuildType({
    name = "Build CoreLibraries Metered"
    description = "Compile CoreLibraries"

    params {
        param("env.METERING", "1")
        param("env.PYTHONPATH", "%system.teamcity.build.checkoutDir%/buildtools/artifactory/")
        param("env.BRANCH_NAME", "${LinuxBuild_Lens7vcs.paramRefs.buildVcsBranch}")
        param("env.BUILD_NUMBER", "${LinuxBuild_CommonServicesAndTools_CoreLibraries_BuildCoreLibrariesUnMetered.depParamRefs.buildNumber}")
		param("env.CHECKOUT_DIR", "%teamcity.build.checkoutDir%")
    }

    vcs {
        root(LinuxBuild.vcsRoots.LinuxBuild_Lens7vcs)

        checkoutMode = CheckoutMode.ON_SERVER
        cleanCheckout = true
        checkoutDir = "%env.PROJECT_ROOT%/LENS7_DEV_CORELIBRARIES"
        showDependenciesChanges = true
    }

    steps {
        script {
            name = "Install Thirdparties"
            workingDir = "3rdparty/"
            scriptContent = """
                conan install . -r prodvirtuallens -s compiler=gcc -s compiler.version=7.5
                            conan build .
            """.trimIndent()
        }
        exec {
            name = "Install Dependencies"
            workingDir = "buildtools/artifactory"
            path = "conan"
            arguments = """
                install
                corelibraries.py
                -if
                build_corelibraries
                -s
                os=Linux
                -s
                compiler=gcc
                -s
                compiler.version=7.5
            """.trimIndent()
        }
        exec {
            name = "Configure"
            workingDir = "buildtools/artifactory"
            path = "conan"
            arguments = """
                source
                corelibraries.py
            """.trimIndent()
        }
        exec {
            name = "Build"
            workingDir = "buildtools/artifactory"
            path = "conan"
            arguments = """
                build
                corelibraries.py
                -if
                build_corelibraries
            """.trimIndent()
        }
        exec {
            name = "Export Receipe"
            workingDir = "buildtools/artifactory"
            path = "conan"
            arguments = """
                export
                corelibraries.py
            """.trimIndent()
        }
        exec {
            name = "Export Package"
            workingDir = "buildtools/artifactory"
            path = "conan"
            arguments = """
                export-pkg
                corelibraries.py
                -f
            """.trimIndent()
        }
        exec {
            name = "Upload"
            workingDir = "buildtools/artifactory"
            path = "sh"
            arguments = "upload.sh"
        }
        script {
            name = "Remove the previous created docker images"
            scriptContent = """
                dos2unix buildtools/*.sh
                dos2unix buildtools/dockerbuild/corebuild/*.sh
                chmod 755 -R buildtools/Dockercheck.sh
                ./buildtools/Dockercheck.sh configtool 1
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        script {
            name = "Copy Files for Configtool's Image"
            enabled = true
            scriptContent = """
                chmod 755 -R docker/build/xray/prepare.sh
                ./docker/build/xray/prepare.sh configtool 0 1
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        script {
            name = "Build and push Configtool docker image"
            enabled = true
            scriptContent = """
                chmod 755 -R docker/build/xray/build.sh
                ./docker/build/xray/build.sh configtool hub.burning-glass.com lenscoreuser L1time@p 1 0
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "")
        }
    }

    triggers {
        finishBuildTrigger {
            buildType = "${LinuxBuild_CommonServicesAndTools_CoreLibraries_UnitTesting.id}"
            successfulOnly = true
        }
        vcs {
            triggerRules = "+:dev/CoreLibraries"
            branchFilter = ""
        }
    }

    failureConditions {
        failOnText {
            conditionType = BuildFailureOnText.ConditionType.REGEXP
            pattern = """[1-9]\d* Error\(s\)"""
            failureMessage = "Compilation Error in CoreLibraries"
            reverse = false
        }
    }

    dependencies {
        snapshot(LinuxBuild_CommonServicesAndTools_CoreLibraries_BuildCoreLibrariesUnMetered) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
        snapshot(LinuxBuild_CommonServicesAndTools_CoreLibraries_UnitTesting) {
            onDependencyFailure = FailureAction.FAIL_TO_START
        }
    }
})
