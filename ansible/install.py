import os
import sys
import argparse
import yaml

from script_generator import *
from ast import literal_eval

class ReadInputInstall:

    def __init__(self, inputPath):
        self.__inputPath = inputPath
        self.__installVariableMap = VariableMapsForScript(1)

    def createVariableMap(self):
        with open(self.__inputPath) as input:
            doc = yaml.safe_load(input)
            input.close()
        for variableName, variableValue in doc.items():
            variableNameLowercase = variableName.lower()
            if variableNameLowercase in ["host", "hosts"]:
                self.__addHostInfoToMap(variableValue)
            elif variableNameLowercase == "vep":
                self.__addVepInfoToMap(variableValue)
            elif variableNameLowercase == "vep fasta":
                self.__addVepFastaInfoToMap(variableValue)
            elif variableNameLowercase == 'vep cache':
                self.__addVepCacheInfoToMap(variableValue)
            elif variableNameLowercase in ["vep plugin", "vep plugins"]:
                self.__addVepPluginInfoToMap(variableValue)
            elif variableNameLowercase == "snpeff":
                self.__addSnpeffInfoToMap(variableValue)
            else:
                print("Cannot parse key " + variableName)

    def __addHostInfoToMap(self, value):
        self.__installVariableMap.setValue("hosts", value, 0)

    def __addVepInfoToMap(self, value):
        self.__installVariableMap.setValue("vep_version_to_install", value, 0)
        self.__installVariableMap.addRoleAtBeginning("install_vep", 0) # insert at the beginning of list as it must be performed first

    def __addVepCacheInfoToMap(self, value):
        self.__installVariableMap.setValue("vep_cache_to_install", self.__makeAllKeysInMapToLowercase(value), 0)
        self.__installVariableMap.addRole("install_vep_cache", 0)

    def __addVepFastaInfoToMap(self, value):
        self.__installVariableMap.setValue("vep_fasta_to_install", self.__makeAllKeysInMapToLowercase(value), 0)
        self.__installVariableMap.addRole("install_vep_fasta", 0)
    
    def __makeAllKeysInMapToLowercase(self, listOfMap):
        listOfMapAfterChange = []
        for n in listOfMap:
            listOfMapAfterChange.append({k.lower(): v for k, v in n.items()})
        return listOfMapAfterChange

    def __addVepPluginInfoToMap(self, value):
        vepPluginsCommandString = ",".join(value)
        self.__installVariableMap.setValue("vep_plugins_to_install", vepPluginsCommandString, 0)
        self.__installVariableMap.addRole("install_vep_plugin", 0)

    def __addSnpeffInfoToMap(self, value):
        self.__installVariableMap.setValue("snpEff_version_to_install", value, 0)
        self.__installVariableMap.addRole("install_snpEff", 0)
    
    def getInputPath(self):
        return self.__inputPath
    
    def setInputPath(self, inputPath):
        self.__inputPath = inputPath
    
    def getInstallVariableMap(self):
        return self.__installVariableMap

class Installer:

    def __init__(self, script_path):
        self.__script_path = script_path

    def readInputAndRunPlaybook(self):
        variableMap = self.__getVariableMapFromInput()
        self.__createAndRunInstallPlaybook(variableMap)

    def __getVariableMapFromInput(self):
        vMap = ReadInputInstall(self.__script_path)
        vMap.createVariableMap()
        variableMap = vMap.getInstallVariableMap()
        return variableMap

    def __createAndRunInstallPlaybook(self, versionMap):
        installPlaybookPath = "installPlaybook.yml"
        createPlaybook = ReplaceValueYAML("script/install_script/installScript.yml", installPlaybookPath, versionMap)
        createPlaybook.createPlaybook()
        self.__runPlaybookAndThenDeleteIt(installPlaybookPath)

    def __runPlaybookAndThenDeleteIt(self, playbookPath):
        os.system('ansible-playbook ' + playbookPath)
        os.remove(playbookPath)

def main(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-i", "--input", help="The directory of the input file")
    args = parser.parse_args()
    
    installObj = Installer(args.input)
    installObj.readInputAndRunPlaybook()

if __name__ == "__main__":
    main(sys.argv[1:])
