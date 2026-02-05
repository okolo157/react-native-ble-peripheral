require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "BlePeripheral"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => "13.0" } # Hardcode a safe version if min_ios_version_supported is missing
  s.source       = { :git => "https://github.com/okolo157/react-native-ble-peripheral.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift,cpp}"
  s.frameworks   = "CoreBluetooth"
  s.swift_version = "5.0"

  install_modules_dependencies(s)
end
