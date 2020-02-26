#!/usr/bin/env groovy
/**
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

//
// Mac Build steps
//

def testSteps(scmVars, String buildDir, List environment, String testList) {
  withEnv(environment) {
      sh """#!/bin/bash
        export IROHA_POSTGRES_PASSWORD=${IROHA_POSTGRES_PASSWORD}; \
        export IROHA_POSTGRES_USER=${IROHA_POSTGRES_USER}; \
        mkdir -p /var/jenkins/${scmVars.GIT_COMMIT}-${BUILD_NUMBER}; \
        initdb -D /var/jenkins/${scmVars.GIT_COMMIT}-${BUILD_NUMBER}/ -U ${IROHA_POSTGRES_USER} --pwfile=<(echo ${IROHA_POSTGRES_PASSWORD}); \
        pg_ctl -D /var/jenkins/${scmVars.GIT_COMMIT}-${BUILD_NUMBER}/ -o '-p 5433 -c max_prepared_transactions=100' -l /var/jenkins/${scmVars.GIT_COMMIT}-${BUILD_NUMBER}/events.log start; \
        ./docker/release/wait-for-it.sh -h localhost -p 5433 -t 30 -- true; \
        psql -h localhost -d postgres -p 5433 -U ${IROHA_POSTGRES_USER} --file=<(echo create database ${IROHA_POSTGRES_USER};)
      """
      sh "cd build; IROHA_POSTGRES_HOST=localhost IROHA_POSTGRES_PORT=5433 ctest --output-on-failure --no-compress-output --tests-regex '${testList}'  --test-action Test || true"

      sh 'python .jenkinsci/helpers/platform_tag.py "Darwin \$(uname -m)" \$(ls build/Testing/*/Test.xml)'
      // Mark build as UNSTABLE if there are any failed tests (threshold <100%)
      xunit testTimeMargin: '3000', thresholdMode: 2, thresholds: [passed(unstableThreshold: '100')], \
        tools: [CTest(deleteOutputFiles: true, failIfNotNew: false, \
        pattern: 'build/Testing/**/Test.xml', skipNoTestFiles: false, stopProcessingIfError: true)]

      sh """
        pg_ctl -D /var/jenkins/${scmVars.GIT_COMMIT}-${BUILD_NUMBER}/ stop && \
        rm -rf /var/jenkins/${scmVars.GIT_COMMIT}-${BUILD_NUMBER}/
      """
  }
}

def buildSteps(int parallelism, List compilerVersions, String build_type, boolean coverage, boolean testing, String testList,
       boolean packagebuild, boolean fuzzing, boolean useBTF, List environment) {
  withEnv(environment) {
    def build, vars, utils
    stage('Prepare Mac environment') {
      scmVars = checkout scm
      build = load '.jenkinsci/build.groovy'
      vars = load ".jenkinsci/utils/vars.groovy"
      utils = load ".jenkinsci/utils/utils.groovy"
      buildDir = 'build'
      compilers = vars.compilerMapping()
      cmakeBooleanOption = [ (true): 'ON', (false): 'OFF' ]
      cmakeBuildOptions = ""
      vcpkg_name = "vcpkg-${env.VCPKG_IROHA_HASH}"
      if (packagebuild){
        cmakeBuildOptions = " --target package "
      }

      utils.ccacheSetup(5)

      if (!fileExists("/opt/dependencies/${vcpkg_name}/scripts/buildsystems/vcpkg.cmake")) {
        local_vcpkg_hash = sh(script: "python .jenkinsci/helpers/hash.py vcpkg", returnStdout: true).trim()
        if ( env.VCPKG_IROHA_HASH == local_vcpkg_hash){
          sh """
            rm -rf /opt/dependencies/${vcpkg_name}
            echo "${java.time.LocalDateTime.now()}: ${scmVars.GIT_LOCAL_BRANCH} start  build /opt/dependencies/${vcpkg_name}..." >> /opt/dependencies/vcpkg-map.txt
            bash vcpkg/build_iroha_deps.sh '/opt/dependencies/${vcpkg_name}' '${env.WORKSPACE}/vcpkg'
            echo "${java.time.LocalDateTime.now()}: ${scmVars.GIT_LOCAL_BRANCH} finish build /opt/dependencies/${vcpkg_name}" >> /opt/dependencies/vcpkg-map.txt
            ls -la /opt/dependencies/${vcpkg_name}
          """
        }else{
          error("Build require VCPKG_IROHA_HASH=${env.VCPKG_IROHA_HASH}, server do not have cache and this branch have ${local_vcpkg_hash}")
        }
      }
    }
    for (compiler in compilerVersions) {
      stage ("build ${compiler}"){
        // Remove artifacts from the previous build
        build.removeDirectory(buildDir)
        build.cmakeConfigure(buildDir,
        "-DCMAKE_CXX_COMPILER=${compilers[compiler]['cxx_compiler']} \
        -DCMAKE_C_COMPILER=${compilers[compiler]['cc_compiler']} \
        -DCMAKE_BUILD_TYPE=${build_type} \
        -DCOVERAGE=${cmakeBooleanOption[coverage]} \
        -DTESTING=${cmakeBooleanOption[testing]} \
        -DFUZZING=${cmakeBooleanOption[fuzzing]} \
        -DPACKAGE_TGZ=${cmakeBooleanOption[packagebuild]} \
        -DUSE_BTF=${cmakeBooleanOption[useBTF]} \
        -DCMAKE_TOOLCHAIN_FILE=/opt/dependencies/${vcpkg_name}/scripts/buildsystems/vcpkg.cmake ")

        build.cmakeBuild(buildDir, cmakeBuildOptions, parallelism)
      }
      if (testing) {
        stage("Test ${compiler}") {
          coverage ? build.initialCoverage(buildDir) : echo('Skipping initial coverage...')
          testSteps(scmVars, buildDir, environment, testList)
          coverage ? build.postCoverage(buildDir, '/usr/local/bin/lcov_cobertura.py') : echo('Skipping post coverage...')
          // We run coverage once, using the first compiler as it is enough
          coverage = false
        }
      }
    }
  }
}

def successPostSteps(scmVars, boolean packagePush, List environment) {
  stage('Mac success PostSteps') {
    withEnv(environment) {
      timeout(time: 600, unit: "SECONDS") {
        if (packagePush) {
          def artifacts = load ".jenkinsci/artifacts.groovy"
          def commit = scmVars.GIT_COMMIT
          // if we use several compilers only the last compiler, used for the build, will be used for iroha.deb and iroha.tar.gz archives
          sh """
            ls -lah ./build
            mv ./build/iroha-*.tar.gz ./build/iroha.tar.gz
          """
          // publish packages
          filePaths = [ '\$(pwd)/build/*.tar.gz' ]
          artifacts.uploadArtifacts(filePaths, sprintf('iroha/macos/%1$s-%2$s-%3$s', [scmVars.GIT_LOCAL_BRANCH, sh(script: 'date "+%Y%m%d"', returnStdout: true).trim(), commit.substring(0,6)]))
        } else {
           archiveArtifacts artifacts: 'build/iroha*.tar.gz', allowEmptyArchive: true
        }
      }
    }
  }
}

def alwaysPostSteps(List environment) {
  stage('Mac always PostSteps') {
    withEnv(environment) {
      sh '''
        set -x
        PROC=$( ps uax | grep postgres | grep 5433 |  grep -o "/var/jenkins/.*" | cut -d' ' -f1 )
        if [ -n "${PROC}" ]; then
          pg_ctl -D ${PROC}/ stop
          rm -rf  ${PROC}
        fi
      '''
      cleanWs()
    }
  }
}
return this