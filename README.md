# Altana is a Bitcoin Wallet for Android provided inbitcoin and powered by Blockstream

## What is Altana?

Altana is a fork of Blockstream Green.

Blockstream Green is a non-custodial Bitcoin wallet - it allows you to safely store, send, and receive your Bitcoin. 
It's a mobile app available for Android and [iOS](https://github.com/Blockstream/green_ios), based on [gdk](https://github.com/blockstream/gdk), our cross-platform wallet library.

They offer a variety of advanced features, such as letting our users set their own spending limits, watch-only access for observers, and our unique multisig security model.
All of these (and more) are explained in more detail [here](https://docs.blockstream.com/green/getting-started/intro.html).

<a href="https://play.google.com/store/apps/details?id=it.inbitcoin.altana" target="_blank">
<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play" height="90"/></a>

## Build

For instructions on how to build the wallet please refer to [BUILD.md](BUILD.md)

## Contributing

Guidelines for contributions can be found in [CONTRIBUTING.md](CONTRIBUTING.md)

## Translations

You can help translating this app [here](https://www.transifex.com/blockstream/blockstream-green/)

## Support

Need help? 

Read [our FAQ](https://greenaddress.it/en/faq.html) or contact us at [info@greenaddress.it](mailto:info@greenaddress.it).  

## License

Altana/Blockstream Green is released under the terms of the GNU General Public License. See [LICENSE](LICENSE) for more information or see https://opensource.org/licenses/GPL-3.0 

## Authenticity

Verifying the APK signing certificate fingerprint is very important for you own security - please follow this steps to make sure the APK you've downloaded is authentic.

Unzip the APK and extract the file ```/META-INF/CERT.RSA```; then run:

```
keytool -printcert -file CERT.RSA
```

You will get the certificate fingerprints; verify it matches with:

```
Certificate fingerprints:
	 SHA1: 32:14:9A:78:E4:73:7C:FB:EC:0D:6D:98:3D:07:5F:75:AD:75:AB:16
	 SHA256: F2:07:80:81:79:B0:F6:5B:58:D1:F7:51:D1:17:59:1B:80:93:D5:75:2F:35:0A:DD:C8:E1:E6:23:31:B3:51:B6
	 Signature algorithm name: SHA256withRSA
	 Version: 3
```


### Acknowledgements

Thanks to [Bitcoin Wallet for Android](https://github.com/schildbach/bitcoin-wallet) for their QR scanning activity source code!

Thanks to [Blockstream team](https://github.com/greenaddress/GreenBits)

