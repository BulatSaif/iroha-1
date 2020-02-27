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
    def utils
    stage('Prepare Windows environment') {
      scmVars = checkout scm
      utils = load ".jenkinsci/utils/utils.groovy"

      local_vcpkg_hash = bat(script: "python .jenkinsci\\helpers\\hash.py vcpkg", returnStdout: true).trim().readLines()[-1].trim()
      vcpkg_path = "C:\\vcpkg-${local_vcpkg_hash}"
      vcpkg_toolchain_file = "${vcpkg_path}\\scripts\\buildsystems\\vcpkg.cmake"

      if (!fileExists(vcpkg_toolchain_file)) {
        powershell """
            \$env:GIT_REDIRECT_STDERR = '2>&1'
            if (Test-Path '${vcpkg_path}' ) { Remove-Item '${vcpkg_path}' -Recurse -Force; }
            Add-Content c:\\vcpkg-map.txt "\$(Get-Date): ${scmVars.GIT_LOCAL_BRANCH} start  build ${vcpkg_path}..."
            .\\.packer\\win\\scripts\\vcpkg.ps1 -vcpkg_path "${vcpkg_path}" -iroha_vcpkg_path "${env.WORKSPACE}\\vcpkg"
            Add-Content c:\\vcpkg-map.txt "\$(Get-Date): ${scmVars.GIT_LOCAL_BRANCH} finish build ${vcpkg_path}"
        """
      }
    }
    for (compiler in compilerVersions) {
      stage ("build ${compiler}"){
        bat """
cmake -H.\\ -B.\\build -DCMAKE_TOOLCHAIN_FILE=${vcpkg_toolchain_file} -G "Visual Studio 16 2019" -A x64 -T host=x64 &&^
cmake --build .\\build --target irohad &&^
cmake --build .\\build --target iroha-cli
        """
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
