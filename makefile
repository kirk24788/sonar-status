target:
	mkdir target

target/SonarSource: target
	cd target && git clone https://github.com/SonarSource/sonarqube.git SonarSource

SonarSourceVersion: target/SonarSource
	cd target/SonarSource && git checkout `grep "<sonar.buildVersion>" ../../pom.xml | cut -d \> -f 2 | cut -d \< -f 1`

prepare-dev-enviroment: SonarSourceVersion
	cd target/SonarSource && ./quick-build.sh
