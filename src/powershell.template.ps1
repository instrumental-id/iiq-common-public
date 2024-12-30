function ToVariableCase {
    param($attributeName)

    if ($attributeName -eq $null) {
        ""
    } else {
        $textInfo = (Get-Culture).textInfo
        $trimmedName = $textInfo.ToTitleCase($attributeName) -Replace '[^A-Za-z0-9_]', ''
        $trimmedName = $trimmedName -Replace '^[0-9]', ''
        $finalName = $trimmedName.substring(0,1).toLower() + $trimmedName.substring(1)
        # Return statement is just the last statement's output
        $finalName
    }
}

function ObjectToMap {
    param($o, $parentName)

    if ($o -eq $null) {
        ""
    } else {
        if ($o.GetType() -Eq [string] -or $o.GetType() -eq [String[]]) {
            $o;
        } elseif ($o.GetType() -Eq [Hashtable]) {
            $o;
        } elseif ($o.GetType() -Eq [Object[]]) {
            $o;
        } elseif ($o.GetType() -Eq [boolean]) {
            $o;
        } elseif ($o.GetType() -Eq [Guid]) {
            $o.ToString();
        } elseif ($o.GetType() -Eq [System.Security.Principal.SecurityIdentifier]) {
            $o.ToString();
        } elseif ($o.GetType() -Eq [DateTime] -or $o.getType() -eq [DateTime[]]) {
            $o;
        } elseif ($o.GetType() -Eq [Int32] -or $o.getType() -eq [Int64]) {
            $o;
        } elseif ($o.GetType().ToString() -eq "System.RuntimeType[]" -or $o.GetType().ToString() -eq "System.Reflection.RuntimeModule[]" -or $o.GetType().ToString() -eq "System.RuntimeType") {
            ""
        } else {
            $resultHash = @{}
            $o.psobject.properties | Foreach { 
                # There are a lot of attributes that take you down a rabbit hole of metadata; ignore them
                if ($_.Value -ne $null -and $_.Name -ne "PropertyNames" -and $_.Name -ne "PropertyCount" -and $_.Name -ne "SyncRoot" -and $_.Name -ne "Modules" -and $_.Name -ne "Module" -and $_.Name -ne "Assembly") {
                    $resultHash[$_.Name] = ObjectToMap $_.Value $_.Name
                }
            }
            $resultHash
        }
    }
}

function ExecuteUserCode {
%%USER_COMMAND%%
}

$ConfirmPreference = "None";
$ErrorActionPreference = "Stop";

try {
        Add-type -path utils.dll
        $sReader = New-Object System.IO.StringReader([System.String]$env:Request);  
        $xmlReader = [System.xml.XmlTextReader]([sailpoint.Utils.xml.XmlUtil]::getReader($sReader));  
        $requestObject = New-Object Sailpoint.Utils.objects.AccountRequest($xmlReader);
        $strResult = [System.String]$env:Result
        if ($strResult -eq "" -OR !$strResult) { 
            $resultObject = New-Object Sailpoint.Utils.objects.ServiceResult;
        } else {
            $sResult = New-Object System.IO.StringReader([System.String]$env:Result);  
      
            # Form the xml reader object   
            $xmlReader_Result = [System.xml.XmlTextReader]([sailpoint.utils.xml.XmlUtil]::getReader($sResult));  
        
            # Create SailPoint objects
            $resultObject = New-Object Sailpoint.Utils.objects.ServiceResult($xmlReader_Result);
        }
        $requestApplication = $requestObject.Application;
        $requestOperation = $requestObject.Operation;
        $requestNativeIdentity = $requestObject.NativeIdentity;
        $requestObjectType = $requestObject.ObjectType;
        $attributes = @{};
        $operations = @{};
        $attributeRequests = @{};
        foreach ($attribute in $requestObject.AttributeRequests) {
            if ($null -eq $attributeRequests[$attribute.Name]) {
                $attributeRequests[$attribute.Name] = @();
            }
            $attributeRequests[$attribute.Name] += $attribute;
            $attributes[$attribute.Name] = $attribute.Value;
            $operations[$attribute.Name] = $attribute.Operation;
            $varName = ToVariableCase($attribute.Name);
            $existing = Get-Variable -Name $varName -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
            if ($null -eq $existing)
            {
                if ($varName -ne "attributes" -and $varName -ne "operations" -and $varName -ne "attributeRequests")
                {
                    New-Variable -name "$varName" -value $attribute.Value;
                    New-Variable -name $( $varName + "Op" ) -value $attribute.Operation;
                }
            }
        }

        ######
        # This is where the substituted script gets invoked
        $commandResult = ExecuteUserCode
        ######

        if ($commandResult) {
            $resultObject.Attributes["output"] = ObjectToMap($commandResult);
        } else {
            $resultObject.Attributes["outString"] = "";
            $resultObject.Attributes["output"] = "";
        }
 } catch [Exception] {
        $ErrorMessage = $_.Exception.ToString();
        $resultObject.Errors.add($ErrorMessage);
 } finally {
        $env:Result = $resultObject.toxml();
        # Add the ResultObject back out
        $resultObject.toxml() | out-file $args[0];
 }
 