/**
 * Sample React Native App
 * https://github.com/facebook/react-native
 *
 * @format
 */

import React, {useState} from 'react';
import {
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  useColorScheme,
  View,
  Button,
  Platform,
  Image,
} from 'react-native';

import {launchImageLibrary} from 'react-native-image-picker';
import RNFS from 'react-native-fs';

import {Colors} from 'react-native/Libraries/NewAppScreen';
import ImageSegmentationModule from './nativeModules/ImageSegmentationModule';

function App(): JSX.Element {
  const [imagePath, setImagePath] = useState<string>('');
  const [outPath, setOutPath] = useState<string>('');

  const isDarkMode = useColorScheme() === 'dark';

  const backgroundStyle = {
    backgroundColor: isDarkMode ? Colors.darker : Colors.lighter,
  };

  const onPress = async () => {
    console.log('We will invoke the native module here!');
    const tempDir = `${RNFS.TemporaryDirectoryPath}${
      Platform.OS === 'ios' ? '' : '/'
    }`;
    const path = `${tempDir}image.jpg`;

    launchImageLibrary({mediaType: 'photo'}, async result => {
      try {
        if (await RNFS.exists(path)) {
          await RNFS.unlink(path);
        }

        const response = result!.assets![0];

        if (response.uri) {
          if (Platform.OS === 'ios') {
            await RNFS.moveFile(response.uri, path);
          } else {
            await RNFS.copyFile(response.uri, path);
          }
          setImagePath(`file://${path}?ts=${Date.now()}`);
          console.log(`file://${path}?ts=${Date.now()}`);
          // setSize({width: response.width, height: response.height});

          const input = `file://${path}`;
          const output = `file://${tempDir}output.jpg`;
          await ImageSegmentationModule.segmentImage(input, output);
          console.log(output);
          setOutPath(output);
        } else {
          // navigation.goBack();
        }
      } finally {
        // setLoading(false);
      }
    });
  };

  return (
    <SafeAreaView style={backgroundStyle}>
      <StatusBar
        barStyle={isDarkMode ? 'light-content' : 'dark-content'}
        backgroundColor={backgroundStyle.backgroundColor}
      />
      <ScrollView
        contentInsetAdjustmentBehavior="automatic"
        style={backgroundStyle}>
        <View
          style={{
            backgroundColor: isDarkMode ? Colors.black : Colors.white,
            ...styles.sectionContainer,
          }}>
          <Text>Hello!</Text>
          <Button
            title="Click to invoke your native module!"
            color="#841584"
            onPress={onPress}
          />
          {imagePath ? (
            <Image source={{uri: imagePath}} width={100} height={100} />
          ) : null}
          {outPath ? (
            <Image source={{uri: outPath}} width={100} height={100} />
          ) : null}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  sectionContainer: {
    marginTop: 32,
    paddingHorizontal: 24,
  },
});

export default App;
