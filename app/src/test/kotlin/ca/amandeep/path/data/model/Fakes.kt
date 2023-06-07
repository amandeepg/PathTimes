package ca.amandeep.path.data.model

object Fakes {
    val SINGLE_ALERT = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>RidePATH Alert</title>
                <meta name=&quotviewport&quot content=&quotwidth=device-width, initial-scale=1.0&quot>
                <style>
                    [class*=&quotcol-&quot] {
                        float: left;
                        padding: 0px;
                    }
            
                    [class*=&quotstation&quot] {
                        padding-left: 0px;
                    }
            
                    body {
                        font-family: sans-serif;
                        font-size: 10pt;
                        padding: 0px;
                        background-color: #FFFFFF;
                    }
            
                        body.dark1bv568 {
                            background-color: #1a1a1a;
                        }
            
                    .stationName {
                        padding-top: 5px;
                        padding-bottom: 5px;
                        padding-left: 10px;
                        background-color: #DFDFDF;
                        color: #666666;
                        font-weight: bold;
                    }
            
                        .stationName.dark1bv568 {
                            background-color: #424242;
                            color: #CCCCCC;
                        }
            
                    .alertText {
                        color: #404040;
                    }
            
                        .alertText.dark1bv568 {
                            color: #DEDEDE
                        }
            
                    a.dark1bv568 {
                        color: #c4f1ff;
                    }
                </style>
            </head>
            <body class=&quot%_defaulttheme_%&quot>
                <div class=&quotrow col-3&quot>
                    <div class=&quotstation %_defaulttheme_%&quot>
                <div class=&quotstationName %_defaulttheme_%&quot>
                    <table style=&quotwidth: 100%; border-collapse: collapse;&quot border=&quot0&quot>
                        <tbody>
                            <tr>
                                <td style=&quotwidth: 50%;&quot><strong><span>6/5/2023</span> </strong></td>
                                <td style=&quotwidth: 50%; text-align: right;&quot><strong><span>10:00 PM</span></strong></td>
                            </tr>
                        </tbody>
                    </table>
                </div><p><span class=&quotalertText %_defaulttheme_%&quot>9 St and 23 St stations closed nightly from approximately 11:59pm until 5am the following morning for maintenance-related activity and periodic cleaning. Christopher St, 14 St, and 33 St entrances remain open.</span> </p>
            </div>
            <br />
            
            
                </div>
            </body>
            </html>
    """.trimIndent()
}
