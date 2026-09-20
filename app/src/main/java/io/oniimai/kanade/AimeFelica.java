/* SPDX-License-Identifier: MPL-2.0 OR GPL-3.0-only
 * Inverse substitution tables and decoding algorithm adapted from
 * zhicheng233/PN532-Aime-Reader, src/NfcAime.Dll/FeliCaDecryptor.cs
 * commit 8feaf84860a17f10ebd12c3827a17cc7deaa01c3.
 * This file is available under MPL-2.0; see licenses/PN532-Aime-Reader-MPL-2.0.txt.
 * Modifications: Java port, strict BCD validation, SEGA Aime issuer allowlist,
 * no card writes or IDm conversion.
 * This modified file is also offered under GPL-3.0-only as part of this GPL
 * Larger Work, under MPL-2.0 section 3.3. Its MPL availability is preserved,
 * including for the local modifications. See LICENSE for GPL terms and warranty
 * disclaimer. This additional offer does not relicense the upstream repository.
 */
package io.oniimai.kanade;

/** Decode the actual encrypted Amusement IC SPAD0 read from the presented card. */
final class AimeFelica {
    private AimeFelica() {}
    private static final String[] TABLE_HEX={
        "243cba36e385a4d0934373b9706ec9f1100e9b2c97e70b636c2920fe86f3e1f5f69fb616047b8fabb1392a1beb5ca8ac3811125f893e7dcaec53db6d1ed281789646fff9541c287a4fd3c0dcc16af2bccb57fd4ae4f0b2c79540625241e2ad49a6b51f02c8da92e5b0c564764821de0f45584cdfa784cfd5154e27806b7ffc44714722dd300ca13be037a03523903274bf8dc2ead650bbd9c483b43168558b5df47218b7eff7982d010361cc0a8e1300e814af095175a52f1d0d658acd66073d05d84be99d997c91d1b819c32b426988c679173a4d5ba25afb253fbdf8edcee687a326a92ebe94087e67608c9c5e6fb39e06fa82aeee5977d71a9a34d456aa33",
        "f9817c00b93037d590516ef0b206fbcd39145ff81bc74a82708c921deac34ec812e6b610d32f95842542a572e58f55ef86a253aeed262047de786828e445cc35bc3ebb8ec0be34afa60964017b44f5f3b13fa4b33280c94f6f40bf214c74e1115eeb3d712e9d6275d41648771367ad6b5d0787f7a89a59768b9b1ed8eeaae9992dc597f1a783fccadcbab84be88917050ba06523d1ce3603d7e0f291df0e9c2c0d66d673585cb40a4dc23bd2b7e2ac33db6027bd56432404028d7d7ff6b5f4fddd5bec79c4221fa18854e71998947e31a329e35acb6ab0cf6d936c7a08a93c1cd06350c68538c14149ab61522a9fd9181a57462b69dafaff0f15963a8afe9e0c",
        "11a29ac394a051015bf7d130ee1f06d57767d3c16ee76a1aa48e9f6566d60c2dc6e469b7565a5760fc791ee11652f007d0cdca78c98bb38827f6e0c084cf2fa304008040a7fa759d4b89462228373413ff20b4da08266c8ff25d7b735c4c9071418a9ebb12e8556d2a254aafbd9519a531baadd9c5a66b83c4b6a9ccf9a1b17adcc7db2e7e7f8d4e620f0339fdb8d418c2ec6102531c3b7dbc1d59f58c98f16f9385f82c74d85e4d97ab87820a21425f0d5833443adde9fb5047c8d7819be51b1754862beff429320e7c2315ebe63c35e296683f0b43cede9cbe4f457276b510e3df92d2a84891b9acbf49b2996470eaf33e6314aeedaa24fe3dcbb009383605",
        "c4f02849554af5fd75af2069c843866bc9a8c6544cdd025be89b597734d7c0515ff37ed4f19081ce198a7833cd978cd66a4b8fa480dc1a1714c5078766ad9d85b2f88d98df3e1f3f645840ac6c5c085d53baffd2a92c25abe432389af9cbc29167d0e322292d9248b40e9905a36f0a15efd5104fd8f600e6462b31578394ae03eb8e1324a11e5ed126d9a7ca366e7137eeb1fa7c7606b823be21bc9ec1da7a3b62277bb0e56318827f0feda02a9cbf11bde0880d3a795256f7b6d309161b70b342602f1ca29f72124547bbe10b018b1ddeec0c4ee9cfcc9574f4a593c7db4dfb3565fee739b5ea96c304414484fc6d30e2f268b7897d733db95a50a63c61aa2e",
        "1a599cadc8e41154ed370f3ae65f3c4bb81589b1e8da697791568bdb0624cf18f8b087df8c35cb86539da4664a7a710a4838ffdc8320ce9832f6d7af705e738a14721ec129790708e94346d02fde2a4f3d2c50b375fc0b64ae317b61a530a093d2bec255ba6cf36268fdac3b95491f6bb485f7a603ec6e9a81090c6aee9e4ef0ab2d7ea1e1bbc9bc41f2fa2e1bdd2734d36004b501d640f9d50247d9d8e08f2bfbb7c5eb5716e5788df400cd8239c696bdbfe336450e261d63e7843eb69b22a3f58e236dc3997d9097102580d44ce274ccb25c33c7ea05123f51a8fef11c42cad176286f92a767a919a2445baaef587f21c08865b95d4d0d5ac413527c9f9417",
        "c72b82615bd09684d34a70a19b59339fc02014532917c50bc97b97020d3a1e7c3f6b52e8753df6e40c8a4fc35f2665733123284874aaa73609b1e291045122fc08a605a4f1121c19eb4037c2a0411dd4dc07438f47afd12e98ab01baf06668acf9e769b6cb8d78871503d5dfa31a9d6aea2f944e9e42d7b83892d8bbdedd9abcb04c79f4583ee98381ffe355fd5db2ef9c6d549960da3becfa11d6c42aed4bae13bfb9068be01f7f5aad90390ff3bd466c2cf2f8fed9e6720e89be5ec6a8cff5577e8cb7e1887ac81b18db6f35d216326463a58ef7b47695867de5a95c8500c1934dfb30a2b5253410773c67712d4445560a50b3cd6280ce214924caee276ecc",
        "a5b06db251551fbf3e8ddd1992ea1bbd326529a48967b1906800da0da65954f210142f45e03b23fad750ac93e643010a5c78709846a97bf395072ad374b33fa36082394e3448c01cf6c291644d3cf89d359a94e37af0f96eb912b8042d02281385728087db2bf14f26a2e1497e9ccca7b6a0d09b3677ad8b6b4a031d058a064bafe531b5d4c60c66ba83fd090fae71b76fdc41e8178e407f6230ffa88425fb16ce3744ab991eeb183a47f75f81cfed582c6cfe9e57539720ca791a5a88f5699fe7d90ebe42df56e44c22aa730b15c5eefcc7d6cbcd8ce27621e9d1ecc87dd88f617c2ebcdeb475d2c4633da15e5d6a0824c927bbef33865bd5385211f4c396c1",
        "345adb2c5957122b30c2a092bfedbc45de279b96d3e6c5ebd8244ba421cca8d6ce3cbab109e0d732664a831d19ca89670f420771e9bb445e8517c4ae9c3f6b78e76d02a7fe33e3d90a7deaf118877b6203795223003e25b9dd16683b1f90a62ac629918a9def1e8476ee4f39fb111b0b93ad49b705e14dcbe838d055f2f70d8b65cdfad49ec9814e14c826b6f9aaf580f86a98ab58f3b88eb5974372a2da64c1405c13b0e5bd089f1c7e8f06be6e501a2f37a1fc102e705304206348e2fd6f7a7561b3353acf5b888251d247a5a9746c31947c9ac0ec150e28012dc3f0b4e48ddcac3d5f86c79522f4b2a35446d1778c0cffaf565d36697f994cd56073df41f6",
        "04da79631ebdeae30b652501cda992ed187557308903096adcc798a450911fb10c778566ca121c67c2e01783599dd5228256a89fc66048b311fca328fb06dd5dba297a4fe8fee910cbf3937b6c6954e744a2841d8dcefffa1a879074a1f814aabcc0cf31b4d9bfd85e262dd0e43f19d68c2fab3958726edf3be63cb66288de40b28f9b7c9543d19eac9cd423e10e4b5333462013341b97f7f1c3614a6f5a217f702e554105c5d776e52715ec425c4d78358befd2eeb5beae023ad35fc424f0f9514eeb000ffdaf3ec19a528681807ef62bccb97d68f2ad99a7072c7338b06bb78e71a0f43da60d37db0a473616966d322a5be24594f5a54cc88a4964bb08c9b8"
    };
    private static final int[][] INVERSE=new int[9][256];
    static {for(int t=0;t<9;t++)for(int i=0;i<256;i++)INVERSE[t][i]=Integer.parseInt(TABLE_HEX[t].substring(i*2,i*2+2),16);}
    static byte[] decode(byte[] encrypted){
        if(encrypted==null||encrypted.length!=16)return null;
        byte[] data=encrypted.clone();for(int i=0;i<16;i++)data[i]=(byte)INVERSE[8][data[i]&255];
        int rounds=((data[15]&255)>>>4)+7,table=(data[15]&255)+5*rounds;
        for(int round=0;round<rounds;round++){
            table-=5;int previous=data[14]&255;
            for(int i=0;i<15;i++){int current=data[i]&255;data[i]=(byte)((current>>>5)|((previous&31)<<3));previous=current;}
            for(int i=0;i<15;i++)data[i]=(byte)INVERSE[table%8][data[i]&255];
        }
        return data;
    }
    static String accessCode(byte[] encrypted){
        String code=AimeProtocol.accessCode(decode(encrypted));
        if(code==null)return null;
        String issuer=code.substring(0,3);
        // Read both legacy and Amusement IC Aime cards, but never turn another
        // publisher's Amusement IC card into a game login. 500 is limited-edition
        // SEGA Aime; 501 is standard SEGA Aime. Keep this check after SPAD decoding.
        return issuer.equals("500")||issuer.equals("501")?code:null;
    }
}
