#!/usr/bin/env groovy
/**
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

//
// Windows Build steps
//

def buildSteps(int parallelism, List compilerVersions, String buildType, boolean coverage, boolean testing, String testList,
       boolean packageBuild, boolean useBTF, List environment) {
  withEnv(environment) {
    stage('Prepare Windows environment') {
      scmVars = checkout scm
      vcpkg_name = "vcpkg-${env.VCPKG_IROHA_HASH}"
      if (!fileExists("C:\\${vcpkg_name}\\scripts\\buildsystems\\vcpkg.cmake)) {
        local_vcpkg_hash = sh(script: "python .jenkinsci/helpers/hash.py vcpkg", returnStdout: true).trim()
        if ( vcpkg_name == local_vcpkg_hash) {
          powershell """
              \$env:GIT_REDIRECT_STDERR = '2>&1'
              if (Test-Path 'C:\\${vcpkg_name}' ) { Remove-Item 'C:\\${vcpkg_name}' -Recurse -Force; }
              Add-Content c:\\vcpkg-map.txt "${java.time.LocalDateTime.now()}: ${scmVars.GIT_LOCAL_BRANCH} start  build C:\\${vcpkg_name}..."
              .\\.packer\\win\\scripts\\vcpkg.ps1 -vcpkg_name "C:\\${vcpkg_name}" -iroha_vcpkg_name "${env.WORKSPACE}\\vcpkg"
              Add-Content c:\\vcpkg-map.txt "${java.time.LocalDateTime.now()}: ${scmVars.GIT_LOCAL_BRANCH} finish build C:\\${vcpkg_name}"
          """
        } else {
          error("Build require VCPKG_IROHA_HASH=${vcpkg_name}, server do not have cache and this branch have ${local_vcpkg_hash}")
        }
      }
    }
    for (compiler in compilerVersions) {
      stage ("build ${compiler}"){
        bat '''
cmake -H.\\ -B.\\build -DCMAKE_TOOLCHAIN_FILE=C:\\${vcpkg_name}\\scripts\\buildsystems\\vcpkg.cmake -G "Visual Studio 16 2019" -A x64 -T host=x64 &&^
cmake --build .\\build --target irohad &&^
cmake --build .\\build --target iroha-cli
        '''
      }
    }
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
