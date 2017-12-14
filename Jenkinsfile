pipeline {
  agent {
    docker {
      image 'maven:3.5.2-jdk-8'
    }
    
  }
  stages {
    stage('build') {
      steps {
        dir(path: 'dockerfiles/compilation/java-8/') {
          sh '''mvn -am -pl mpt/impl/imap-mailbox/inmemory install -DskipTests -P inmemory'''
        }
      }
    }

    stage('test') {
      steps {
        script {
          def splits = splitTests parallelism: [$class: 'CountDrivenParallelism', size: 4], generateInclusions: true

          def testGroups = [:]

          for (int i = 0; i < splits.size(); i++) {
            def j = i
            def split = splits[j]

            testGroups["split-${j}"] = {
              node {
                checkout scm
                def mavenTest = 'mvn -pl mpt/impl/imap-mailbox/inmemory test -P inmemory -DMaven.test.failure.ignore=true'

                if (split.includes) {
                  writeFile file: "target/parallel-test-includes-${j}.txt", text: split.list.join("\n")
                  mavenTest += " -Dsurefire.includesFile=target/parallel-test-includes-${j}.txt"
                } else {
                  writeFile file: "target/parallel-test-excludes-${j}.txt", text: split.list.join("\n")
                  mavenTest += " -Dsurefile.excludesFile=target/parallel-test-excludes-${j}.txt"
                }

                sh "cat target/parallel-test-excludes-${j}.txt"
                sh "cat target/parallel-test-includes-${j}.txt"
                sh mavenTest

                junit '**/target/surefire-reports/TEST-*.xml'
              }
            }
          }

          parallel testGroups
        }
      }
    }
  }
}
