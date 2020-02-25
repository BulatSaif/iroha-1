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
      powershell """
        if(!(Test-Path C:\\vcpkg-1.1.x\\scripts\\buildsystems\\vcpkg.cmake))
        {
          Remove-Item 'C:\\vcpkg-1.1.x' -Recurse -Force
          Add-Content c:\\vcpkg-map.txt "${scmVars.GIT_LOCAL_BRANCH} start  build C:\\vcpkg-1.1.x..."
          .\\.packer\\win\\scripts\\vcpkg.ps1 -vcpkg_path "C:\\vcpkg-1.1.x" -iroha_vcpkg_path "${env.WORKSPACE}\\vcpkg"
          Add-Content c:\\vcpkg-map.txt "${scmVars.GIT_LOCAL_BRANCH} finish build C:\\vcpkg-1.1.x"
        }
      """
    }
    for (compiler in compilerVersions) {
      stage ("build ${compiler}"){
        bat '''
cmake -H.\\ -B.\\build -DCMAKE_TOOLCHAIN_FILE=C:\\vcpkg-1.1.x\\scripts\\buildsystems\\vcpkg.cmake -G "Visual Studio 16 2019" -A x64 -T host=x64 &&^
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
