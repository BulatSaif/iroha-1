#!/usr/bin/env groovy
/**
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

//
// Windows Build steps
//

def testSteps(String buildDir, List environment, String testList) {
  withEnv(environment) {
    bat "cd .\\${buildDir} & del /q /f /s Test.xml & ctest --output-on-failure --no-compress-output --tests-regex \"${testList}\" --test-action Test || exit 0"
    bat "for /f \"usebackq tokens=*\" %%a in (`dir .\\${buildDir} /s /b ^| findstr Testing ^| findstr Test.xml`) do python .\\.jenkinsci\\helpers\\platform_tag.py \"Windows %PROCESSOR_ARCHITECTURE%\" %%a"
    // Mark build as UNSTABLE if there are any failed tests (threshold <100%)
    xunit testTimeMargin: '3000', thresholdMode: 2, thresholds: [passed(unstableThreshold: '100')], \
      tools: [CTest(deleteOutputFiles: true, failIfNotNew: false, \
      pattern: "${buildDir}/Testing/**/Test.xml", skipNoTestFiles: false, stopProcessingIfError: true)]
  }
}

def buildSteps(int parallelism, List compilerVersions, String buildType, boolean coverage, boolean testing, String testList,
       boolean packageBuild, boolean useBTF, List environment) {
  withEnv(environment) {
    scmVars = checkout scm
    xunit testTimeMargin: '3000', thresholdMode: 2, thresholds: [passed(unstableThreshold: '100')], \
      tools: [CTest(deleteOutputFiles: true, failIfNotNew: false, \
      pattern: "Testing/20191228-0830/Test.xml", skipNoTestFiles: false, stopProcessingIfError: true)]
    /*buildDir = 'build'
    for (compiler in compilerVersions) {
      stage ("build ${compiler}"){
        bat """
call \"C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\BuildTools\\VC\\Auxiliary\\Build\\vcvars64.bat\" &&^
cmake -H.\\ -B.\\${buildDir} -DBENCHMARKING=ON -DCMAKE_TOOLCHAIN_FILE=C:\\vcpkg\\scripts\\buildsystems\\vcpkg.cmake -GNinja &&^
cmake --build .\\${buildDir}
        """
      }
      if (testing) {
          stage("Test ${compiler}") {
            // coverage ? build.initialCoverage(buildDir) : echo('Skipping initial coverage...')
            testSteps(buildDir, environment, testList)
            // coverage ? build.postCoverage(buildDir, '/tmp/lcov_cobertura.py') : echo('Skipping post coverage...')
            // We run coverage once, using the first compiler as it is enough
            // coverage = false
          }
        } //end if
    } //end for*/
  }
}

def successPostSteps(scmVars, boolean packagePush, List environment) {
  stage('Windows success PostSteps') {
    withEnv(environment) {
      if (packagePush){
        timeout(time: 600, unit: "SECONDS") {
           archiveArtifacts artifacts: 'build\\bin\\Debug\\iroha*.exe', allowEmptyArchive: true
        }
      }
    }
  }
}

def alwaysPostSteps(List environment) {
  stage('Windows always PostSteps') {
    withEnv(environment) {
      cleanWs()
    }
  }
}
return this
