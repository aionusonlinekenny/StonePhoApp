<?php
header('Content-Type: text/plain');
ini_set('display_errors', 1);
error_reporting(E_ALL);

echo "1. Checking existing constants\n";
echo "   MID defined? " . (defined('MID') ? 'YES = ' . MID : 'NO') . "\n";
echo "   TOKEN defined? " . (defined('TOKEN') ? 'YES' : 'NO') . "\n";
echo "   BASE defined? " . (defined('BASE') ? 'YES = ' . BASE : 'NO') . "\n";
echo "   API_KEY defined? " . (defined('API_KEY') ? 'YES' : 'NO') . "\n";

echo "2. Defining constants\n";
if (!defined('API_KEY')) define('API_KEY', 'StonePhoClover@2024');
if (!defined('MID'))     define('MID',    'GW3XFCV71AK81');
if (!defined('TOKEN'))   define('TOKEN',  'c30698f2-347e-add6-b758-44285d0e6cac');
if (!defined('BASE'))    define('BASE',   'https://api.clover.com/v3/merchants/' . MID);

echo "   Done | BASE=" . BASE . "\n";

echo "3. GET params\n";
echo "   action=" . (isset($_GET['action']) ? $_GET['action'] : 'not set') . "\n";
echo "   key=" . (isset($_GET['key']) ? $_GET['key'] : 'not set') . "\n";
echo "   key match? " . ((isset($_GET['key']) && $_GET['key'] === 'StonePhoClover@2024') ? 'YES' : 'NO') . "\n";

echo "4. Auth header check\n";
$h = isset($_SERVER['HTTP_AUTHORIZATION']) ? $_SERVER['HTTP_AUTHORIZATION'] : '(none)';
echo "   HTTP_AUTHORIZATION=" . $h . "\n";

echo "5. DONE - all OK\n";
