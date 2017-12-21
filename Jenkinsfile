pipeline {
  agent {
    docker {
      image 'maven:3.5.2-jdk-8'
      args '-v /root/.m2:/root/.m2'
    }
    
  }
  stages {
    stage('build') {
      steps {
        dir(path: 'dockerfiles/compilation/java-8/') {
          sh '''mvn -B -am -pl mpt/impl/imap-mailbox/inmemory install -DskipTests -P inmemory'''
          stash name: 'build', includes: '*'
        }
      }
    }

    stage('test') {

      steps {
        script {
          def splits = splitTests parallelism: [$class: 'CountDrivenParallelism', size: 4], generateInclusions: true
          def testGroups = [:]
          parallel testGroups

          for (int i = 0; i < splits.size(); i++) {
            def j = i
            def split = splits[j]

            testGroups["split-${j}"] = {
              node {
                unstash 'build'
                def mavenTest = 'mvn -B -pl mpt/impl/imap-mailbox/inmemory test -P inmemory -DMaven.test.failure.ignore=true'

                if (split.list.size() > 0) {
                  if (split.includes) {
                    def includes = split.list.join("\n")
                    sh "echo \"${includes}\" >> target/parallel-test-includes-${j}.txt"

                    mavenTest += " -DincludesFile=target/parallel-test-includes-${j}.txt"
                  } else {
                    def excludes = split.list.join("\n")
                    sh "echo \"${excludes}\" >> target/parallel-test-excludes-${j}.txt"

                    mavenTest += " -DexcludesFile=target/parallel-test-excludes-${j}.txt"
                  }
                }

                sh mavenTest

                sh "find . -name TEST-*.xml"
              }
            }
          }

          parallel testGroups

          sh "find . -name TEST-*.xml"
          step([$class: "JUnitResultArchiver", testResults: '**/target/surefire-reports/TEST-*.xml'])
        }
      }
    }
  }
}
