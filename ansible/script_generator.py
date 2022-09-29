"""
Replace text in Ansible script so that it can produce the right script
with the right variable

"""
import yaml

class ReplaceValueYAML:

    def __init__(self, pathFrom, pathTo, variableMap):
        self.pathFrom = pathFrom
        self.pathTo = pathTo
        self.variableMap = variableMap
    
    def createPlaybook(self):
        with open(self.pathFrom) as input:
            doc = yaml.safe_load(input)
            input.close()
        for playIndex in range(self.variableMap.numberOfPlays()):
            playbook = doc[playIndex]
            playbook["hosts"] = self.variableMap.getValue("hosts", playIndex, "all")
            playbook["roles"] = self.variableMap.getValue("roles", playIndex, [])
            varPlaybook = playbook.get("vars", {})
            for k in varPlaybook.keys():
                varPlaybook[k] = self.variableMap.getValue(k, playIndex,  varPlaybook[k])
        with open(self.pathTo, 'w') as output:
            yaml.dump(doc, output)
            output.close()

    def getPathFrom(self):
        return self.pathFrom
    def getPathTo(self):
        return self.pathTo
    def getVariableMap(self):
        return self.variableMap

    def setPathFrom(self, pathFrom):
        self.pathFrom = pathFrom
    def setPathTo(self, pathTo):
        self.pathTo = pathTo
    def setVariableMap(self, variableMap):
        self.variableMap = variableMap


class VariableMapsForScript:
    
    def __init__(self, numberOfPlay):
        self.__listOfPlays = []
        if numberOfPlay<1:
            # TODO: maybe throw an exception here
            numberOfPlay = 1
        for i in range(numberOfPlay):
            self.__listOfPlays.append({"hosts": "", "roles": []})

    def addRole(self, roleName, playIndex):
        self.__listOfPlays[playIndex]["roles"].append(roleName)
    
    def addRoleAtBeginning(self, roleName, playIndex):
        self.__listOfPlays[playIndex]["roles"].insert(0, roleName)

    def getValue(self, key, playIndex, defaultValue=None):
        play = self.__listOfPlays[playIndex]
        if(key not in play):
            return defaultValue
        else:
            return play[key]

    def setValue(self, key, value, playIndex):
        self.__listOfPlays[playIndex][key] = value

    def updatePlayFrom(self, mapWithNewValue, playIndex):
        self.__listOfPlays[playIndex].update(mapWithNewValue)

    def isThereRoles(self, playIndex):
        return len(self.__listOfPlays[playIndex]["roles"])>0
    
    def numberOfPlays(self):
        return len(self.__listOfPlays)
