import os
import sys
import argparse
import yaml

from script_generator import *
from ast import literal_eval


class CheckVersionInputReader:

    def __init__(self, inputPath):
        self.__inputPath = inputPath
        self.__versionVariableMap = VariableMapsForScript(1)
        self.__autoVariableMap = VariableMapsForScript(1)
        self.__autoVariableMap.setValue("output_ansible_txt_path", "getVersionAutoOutput.txt", 0)

    def createVariableMap(self):
        self.readInput()
        if(self.__isThereAutoCheckToDo()):
            self.__getValueAutoForAnsibleScript()
            self.__readAutoValueForAnsibleScript()

    def readInput(self):
        with open(self.__inputPath) as input:
            doc = yaml.safe_load(input)
            input.close()
        for variableName, variableValue in doc.items():
            variableNameLowercase = variableName.lower()
            # host
            if variableNameLowercase in ["hosts", "host"]:
                self.__addHostInfoToMap(variableValue)
            # distribution
            elif variableNameLowercase == "distribution":
                self.__addDistributionInfoToMap(variableValue)
            # vep version
            elif variableNameLowercase in ["vep", "vep version"]:
                self.__addVepInfoAndRoleToMap(variableValue)
            # vep cache
            elif variableNameLowercase == "vep cache":
                self.__addVepCacheInfoAndRoleToMap(variableValue)
            #vep plugin
            elif variableNameLowercase in ["vep plugin", "vep plugins"]:
                self.__addVepPluginInfoAndRoleToMap(variableValue)
            # snpeff
            elif variableNameLowercase in ["snpeff", "snpeff version"]:
                self.__addSnpeffInfoAndRoleToMap(variableValue)
            else:
                print("Cannot parse key " + variableName)
    
    def __addHostInfoToMap(self, value):
        self.__versionVariableMap.setValue("hosts", value, 0)
        self.__autoVariableMap.setValue("hosts", value+"[0]", 0)

    def __addDistributionInfoToMap(self, value):
        self.__versionVariableMap.addRole("check_distribution", 0)
        if self.__isTheValueAuto(value):
            self.__autoVariableMap.addRole("get_distribution_auto", 0)
        else:
            v_split = value.split()
            self.__versionVariableMap.setValue("distribution", v_split[0], 0)  # eg. Ubuntu
            self.__versionVariableMap.setValue("distribution_version", v_split[1], 0) # eg. 18.04
            self.__versionVariableMap.setValue("distribution_major_version", v_split[1].split(".")[0], 0)  # eg. 18

    def __addVepInfoAndRoleToMap(self, value):
        self.__versionVariableMap.addRole("check_vep_version", 0)
        if self.__isTheValueAuto(value):
            self.__autoVariableMap.addRole("get_vep_version_auto", 0)
        else:
            v_split = str(value).split(".")
            self.__versionVariableMap.setValue("ensembl_vep_version", value, 0)
            self.__versionVariableMap.setValue("major_vep_version", v_split[0], 0)

    def __addVepCacheInfoAndRoleToMap(self, value):
        if self.__isTheValueAuto(value):
            self.__autoVariableMap.addRole("get_vep_cache_map_auto", 0)
            self.__versionVariableMap.addRole("check_vep_cache_auto", 0)
        else:
            self.__versionVariableMap.setValue("vep_cache_directory_list", value, 0)
            self.__versionVariableMap.addRole("check_vep_cache_manual", 0)

    def __addVepPluginInfoAndRoleToMap(self, value):
        if self.__isTheValueAuto(value):
            self.__autoVariableMap.addRole("get_vep_plugin_list_auto", 0)
            self.__versionVariableMap.addRole("check_vep_plugin_auto", 0)
        else:
            self.__versionVariableMap.setValue("vep_plugin_file_list", value, 0) 
            self.__versionVariableMap.addRole("check_vep_plugin_manual", 0)

    def __addSnpeffInfoAndRoleToMap(self, value):
        self.__versionVariableMap.addRole("check_snpeff_version", 0)
        if self.__isTheValueAuto(value):
            self.__autoVariableMap.addRole("get_snpeff_version_auto", 0)
        else:
            self.__versionVariableMap.setValue("snpEff_version", value, 0)

    def __isTheValueAuto(self, value):
        return str(value).lower() == "auto"

    def __isThereAutoCheckToDo(self):
        return self.__autoVariableMap.isThereRoles(0)

    def __readAutoValueForAnsibleScript(self):
        versionOutputFile = open("getVersionAutoOutput.txt", "r")
        versionOutput = literal_eval(versionOutputFile.read())
        self.__versionVariableMap.updatePlayFrom(versionOutput, 0)
        os.remove('getVersionAutoOutput.txt')

    def __getValueAutoForAnsibleScript(self):
        valueAuto = ReplaceValueYAML(
            "script/checkVersion_script/getVersionAutoScript.yml", "getVersionAuto.yml", self.__autoVariableMap)
        valueAuto.createPlaybook()
        os.system('ansible-playbook getVersionAuto.yml')
        os.remove('getVersionAuto.yml')

    def setInputPath(self, inputPath):
        self.__inputPath = inputPath

    def getInputPath(self):
        return self.__inputPath

    def getVersionVariableMap(self):
        return self.__versionVariableMap


class VersionChecker:

    script_path = ""

    def __init__(self, script_path):
        self.script_path = script_path

    def readInputAndRunPlaybook(self):
        variableMap = self.__getVariableMapFromInput()
        self.__createAndRunCheckVersionPlaybook(variableMap)

    def __getVariableMapFromInput(self):
        variableMapCreator = CheckVersionInputReader(self.script_path)
        variableMapCreator.createVariableMap()
        variableMap = variableMapCreator.getVersionVariableMap()
        return variableMap

    def __createAndRunCheckVersionPlaybook(self, versionMap):
        checkVersionPlaybookPath = "checkVersion.yml"
        checkVersionPlaybookCreator = ReplaceValueYAML(
            "script/checkVersion_script/checkVersionScript.yml", checkVersionPlaybookPath, versionMap)
        checkVersionPlaybookCreator.createPlaybook()
        self.__runPlaybookAndThenDeleteIt(checkVersionPlaybookPath)

    def __runPlaybookAndThenDeleteIt(self, playbookPath):
        os.system('ansible-playbook ' + playbookPath)
        os.remove(playbookPath)

def main(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-i", "--input", help="The directory of the input file")
    args = parser.parse_args()

    checkVerObj = VersionChecker(args.input)
    checkVerObj.readInputAndRunPlaybook()

if __name__ == "__main__":
    main(sys.argv[1:])
