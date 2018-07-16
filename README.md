# cloudbees-shared-libraries
### Shared Libraries for needed to run our YAML data-driven [cloudbees-pipelines](https://github.com/emartin-pentaho/cloudbees-pipelines)

**Here are the steps required to set this up manually in a Jenkins master**

1. Click Jenkins/Manage Jenkins
2. Goto Global Pipeline Libraries
3. Set Library Name to 'jenkins-shared-libraries'
4. Set Default Version to 'master'
5. Set Load implicitly to 'false'
6. Set Allow default version to be overridden to 'true'
7. Set Include @Library changes in job recent changes to 'true'
8. Set Retrieval method to 'Modern SCM'
9. Goto Source Code Management section and ...
10. Set Project Repository to 'https://github.com/emartin-pentaho/cloudbees-shared-libraries.git'
11. Set Credentials to a Jenkins credential with read access to that repository.
12. Click the Behaviors Add button and add 'Discover branches'
13. Save the configuration changes.
