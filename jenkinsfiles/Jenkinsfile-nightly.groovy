import hudson.model.*

pipeline {
	parameters {
		gitParameter branchFilter: 'origin_source/(.*)',
			defaultValue: 'dev',
			name: 'MANUAL_GIT_BRANCH',
			type: 'PT_BRANCH',
			selectedValue: 'TOP',
			sortMode: 'ASCENDING',
			useRepository: "${helper.resolveRepoName()}"
		booleanParam name: 'SHOULD_PUBLISH_FAIL_JOB_STATUS', description: 'true to publish job status if failed', defaultValue: true
		booleanParam name: 'WAIT_FOR_BUILDS', description: 'true to wait for trigger build to complete', defaultValue: true
	}

	agent {
		label 'ubuntu-agent'
	}

	triggers {
		//cron('@midnight')
		cron('H/5 * * * *')
	}

	options {
		ansiColor('css')
		timestamps()
	}

	stages {
		stage('display environment') {
			steps {
				sh 'printenv'
				sh 'ls -la'
			}
		}
		stage('checkout') {
			steps {
				script {
					gitCheckout(helper.resolveBranchName(env.MANUAL_GIT_BRANCH), env.REPO_CRED_ID, env.REPO_GIT_URL)
				}
			}
		}
		stage('trigger build jobs') {
			when {
				expression {
					return fileExists(helper.resolveBuildConfigurationFile()) && null != env.WAIT_FOR_BUILDS
				}
			}

			steps {
				script {
					triggerAllJobs(env.JOB_NAME, env.JOB_BASE_NAME, helper.resolveBranchName(env.MANUAL_GIT_BRANCH), env.WAIT_FOR_BUILDS.toBoolean())
				}
			}
		}
	}
	post {
		unsuccessful {
			script {
				if (null != env.SHOULD_PUBLISH_FAIL_JOB_STATUS && env.SHOULD_PUBLISH_FAIL_JOB_STATUS.toBoolean()) {
					helper.sendDiscordNotification (
						"Jenkins Job Failed for ${currentBuild.fullDisplayName}",
						"At least one job failed for Build#${env.BUILD_NUMBER} which has a result of ${currentBuild.currentResult}.",
						env.BUILD_URL,
						currentBuild.currentResult
					)
				}
			}
		}
	}
}

void triggerAllJobs(String jobName, String jobBaseName, String branchName, boolean waitForDownStream) {
	final String pathSeparator = '/'
	String filepath = helper.resolveBuildConfigurationFile()

	Map<String, String>  jobInfos = [:]
	yamlHelper.readYamlFromFile(filepath).builds.each { 
		jobInfos.put(it.path.tokenize(pathSeparator).last(), it.name)
	}

	String currentJobfolder = jobName - jobBaseName
	Map<String, Closure> buildJobs = [:]
	jobInfos.each { jobInfo ->
		String displayName = jobInfo.value
		buildJobs["${displayName}"] = {
			stage("${displayName}") {
				String pipelineName = jobInfo.key
				String fullJobName = currentJobfolder + pipelineName + pathSeparator + branchName

				// Platform parameter can vary per project, get the last value
				String platform = getLastJobParameter(fullJobName, 'PLATFORM')

				build job: "${fullJobName}", parameters: [
					gitParameter(name: 'MANUAL_GIT_BRANCH', value: branchName),
					string(name: 'PLATFORM', value: platform ?: 'ubuntu'),
					string(name: 'BUILD_CONFIGURATION', value: 'release-private'),
					string(name: 'TEST_MODE', value: 'code-coverage'),
					booleanParam(name: 'SHOULD_PUBLISH_IMAGE', value: false),
					booleanParam(name: 'SHOULD_PUBLISH_FAIL_JOB_STATUS', value: SHOULD_PUBLISH_FAIL_JOB_STATUS.toBoolean())],
					wait: waitForDownStream
			}
		}
	}

	parallel buildJobs
}

Object getLastJobParameter(String fullJobName, String parameterName) {
	Item job = Jenkins.get().getItemByFullName(fullJobName)
	ParametersAction params = job.getLastBuild().getAction(hudson.model.ParametersAction)
	return params.getParameter(parameterName).getValue()
}
