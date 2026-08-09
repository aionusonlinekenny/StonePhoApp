<?php
ini_set('display_errors', 1);
error_reporting(E_ALL);

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');

define('SCRIPT_VER', 'v20260727a');
define('API_KEY',    'StonePhoClover@2024');
define('MID',        'GW3XFCV71AK81');
define('TOKEN',      'c30698f2-347e-add6-b758-44285d0e6cac');
define('BASE',       'https://api.clover.com/v3/merchants/' . MID);

function get_sent_key() {
    $h = isset($_SERVER['HTTP_AUTHORIZATION']) ? $_SERVER['HTTP_AUTHORIZATION'] : '';
    if (!$h && isset($_SERVER['REDIRECT_HTTP_AUTHORIZATION'])) {
        $h = $_SERVER['REDIRECT_HTTP_AUTHORIZATION'];
    }
    if (!$h && function_exists('apache_request_headers')) {
        $all = apache_request_headers();
        if (isset($all['Authorization']))       $h = $all['Authorization'];
        elseif (isset($all['authorization']))   $h = $all['authorization'];
    }
    $fromHeader = trim(str_replace('Bearer ', '', $h));
    return $fromHeader ? $fromHeader : (isset($_GET['key']) ? $_GET['key'] : '');
}

if (get_sent_key() !== API_KEY) {
    http_response_code(401);
    die(json_encode(array('error' => 'Unauthorized', 'ver' => SCRIPT_VER)));
}

function clover_call($url) {
    $ch = curl_init($url);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    curl_setopt($ch, CURLOPT_HTTPHEADER, array(
        'Authorization: Bearer ' . TOKEN,
        'Accept: application/json'
    ));
    curl_setopt($ch, CURLOPT_TIMEOUT, 20);
    curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 10);
    $body = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $err  = curl_error($ch);
    curl_close($ch);
    return array(
        'code' => $code,
        'body' => ($body !== false) ? $body : '',
        'err'  => $err
    );
}

function clover($path) {
    $r = clover_call(BASE . $path);
    if ($r['err']) {
        http_response_code(502);
        die(json_encode(array('error' => 'curl: ' . $r['err'], 'ver' => SCRIPT_VER)));
    }
    if ($r['code'] < 200 || $r['code'] >= 300) {
        http_response_code(502);
        die(json_encode(array(
            'error'  => 'Clover HTTP ' . $r['code'],
            'detail' => substr($r['body'], 0, 300),
            'ver'    => SCRIPT_VER
        )));
    }
    return $r['body'];
}

$action = isset($_GET['action']) ? $_GET['action'] : 'orders';

switch ($action) {

    case 'ping':
        $r = clover_call('https://api.clover.com/v3/merchants/' . MID);
        $merchant = json_decode($r['body'], true);
        die(json_encode(array(
            'ok'            => true,
            'ver'           => SCRIPT_VER,
            'php'           => phpversion(),
            'mid'           => MID,
            'clover_http'   => $r['code'],
            'merchant_name' => isset($merchant['name']) ? $merchant['name'] : null,
            'clover_err'    => $r['err'] ? $r['err'] : null,
            'time'          => date('Y-m-d H:i:s T'),
        )));

    case 'orders':
        // Fetch with payments expand, filter server-side — more reliable than Android Gson parsing.
        $raw     = json_decode(clover(
            '/orders?orderBy=createdTime+DESC'
            . '&expand=lineItems%2ClineItems.item%2CorderType%2Cpayments'
            . '&limit=50'
        ), true);
        $all      = isset($raw['elements']) ? $raw['elements'] : array();
        $cutoffMs = ((float)time() - 8.0 * 3600) * 1000.0;
        $open     = array();
        foreach ($all as $o) {
            $pmts    = isset($o['payments']['elements']) ? $o['payments']['elements'] : array();
            $hasPaid = count($pmts) > 0;
            $created = isset($o['createdTime']) ? (float)$o['createdTime'] : 0.0;
            $state   = strtolower(isset($o['state']) ? $o['state'] : '');
            $stateOk = !in_array($state, array('deleted', 'closed'));
            if (!$hasPaid && $stateOk && $created >= $cutoffMs) {
                $open[] = $o;
            }
        }
        $raw['elements'] = $open;
        die(json_encode($raw));

    case 'rest_orders':
        die(clover(
            '/orders?orderBy=createdTime+DESC'
            . '&expand=lineItems%2ClineItems.item%2CorderType%2Cpayments'
            . '&limit=30'
        ));

    case 'debug':
        die(clover('/orders?orderBy=createdTime+DESC&limit=20'));

    case 'states':
        $raw  = json_decode(clover('/orders?orderBy=createdTime+DESC&limit=30'), true);
        $list = array();
        $elems = isset($raw['elements']) ? $raw['elements'] : array();
        foreach ($elems as $o) {
            $list[] = array(
                'id'           => isset($o['id'])           ? $o['id']           : '',
                'title'        => isset($o['title'])        ? $o['title']        : '',
                'state'        => isset($o['state'])        ? $o['state']        : '',
                'paymentState' => isset($o['paymentState']) ? $o['paymentState'] : '',
                'total'        => isset($o['total'])        ? $o['total']        : 0,
            );
        }
        die(json_encode(array('count' => count($list), 'orders' => $list, 'ver' => SCRIPT_VER)));

    case 'atomic':
        $r = clover_call('https://api.clover.com/v3/merchants/' . MID . '/atomic_order/orders?limit=20');
        die(($r['body'] !== '') ? $r['body'] : json_encode(array('error' => $r['err'], 'code' => $r['code'])));

    case 'open_tables':
        // Debug: show exactly what the 'orders' action sends to the Android app after filtering
        $raw2    = json_decode(clover(
            '/orders?orderBy=createdTime+DESC'
            . '&expand=lineItems%2ClineItems.item%2CorderType%2Cpayments'
            . '&limit=50'
        ), true);
        $all2     = isset($raw2['elements']) ? $raw2['elements'] : array();
        $cutoff2  = ((float)time() - 8.0 * 3600) * 1000.0;
        $result2  = array();
        foreach ($all2 as $o) {
            $pmts    = isset($o['payments']['elements']) ? $o['payments']['elements'] : array();
            $hasPaid = count($pmts) > 0;
            $created = isset($o['createdTime']) ? (float)$o['createdTime'] : 0;
            $state   = strtolower(isset($o['state']) ? $o['state'] : '');
            $stateOk = !in_array($state, array('deleted', 'closed'));
            $passAge = $created >= $cutoff2;
            $result2[] = array(
                'title'   => isset($o['title']) && $o['title'] !== '' ? $o['title'] : '(no title)',
                'state'   => $state,
                'hasPaid' => $hasPaid,
                'passAge' => $passAge,
                'KEPT'    => (!$hasPaid && $stateOk && $passAge),
            );
        }
        die(json_encode(array('ver' => SCRIPT_VER, 'cutoffMs' => $cutoff2, 'orders' => $result2)));

    case 'check_payments':
        // Debug: show title + payment count for recent orders — verify payments expand works
        $raw   = json_decode(clover(
            '/orders?orderBy=createdTime+DESC&expand=payments&limit=20'
        ), true);
        $elems = isset($raw['elements']) ? $raw['elements'] : array();
        $list  = array();
        foreach ($elems as $o) {
            $pmts = isset($o['payments']['elements']) ? $o['payments']['elements'] : array();
            $list[] = array(
                'id'           => isset($o['id'])    ? substr($o['id'], -6)    : '',
                'title'        => isset($o['title']) ? $o['title']             : '(no title)',
                'state'        => isset($o['state']) ? $o['state']             : '',
                'paymentCount' => count($pmts),
                'hasPaid'      => count($pmts) > 0,
            );
        }
        die(json_encode(array('ver' => SCRIPT_VER, 'orders' => $list)));

    case 'tables':
        die(clover('/tables?limit=200'));

    case 'categories':
        die(clover('/categories?limit=200'));

    case 'items':
        $raw  = json_decode(clover('/items?expand=categories&limit=1000'), true);
        $all  = isset($raw['elements']) ? $raw['elements'] : array();
        // Bỏ qua item ẩn và item không có danh mục
        $visible = array_values(array_filter($all, function($item) {
            $hidden = isset($item['hidden']) && $item['hidden'] === true;
            $hasCat = isset($item['categories']['elements']) && count($item['categories']['elements']) > 0;
            return !$hidden && $hasCat;
        }));
        $raw['elements'] = $visible;
        die(json_encode($raw));

    case 'delete_order':
        $orderId = isset($_GET['order_id']) ? trim($_GET['order_id']) : '';
        if ($orderId === '') {
            http_response_code(400);
            die(json_encode(array('error' => 'Missing order_id', 'ver' => SCRIPT_VER)));
        }
        $ch = curl_init(BASE . '/orders/' . rawurlencode($orderId));
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_CUSTOMREQUEST, 'DELETE');
        curl_setopt($ch, CURLOPT_HTTPHEADER, array(
            'Authorization: Bearer ' . TOKEN,
            'Accept: application/json'
        ));
        curl_setopt($ch, CURLOPT_TIMEOUT, 20);
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 10);
        $delBody = curl_exec($ch);
        $delCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $delErr  = curl_error($ch);
        curl_close($ch);
        if ($delErr) {
            http_response_code(502);
            die(json_encode(array('error' => 'curl: ' . $delErr, 'ver' => SCRIPT_VER)));
        }
        if ($delCode >= 200 && $delCode < 300) {
            die(json_encode(array('ok' => true, 'deleted' => $orderId, 'ver' => SCRIPT_VER)));
        }
        http_response_code(502);
        die(json_encode(array(
            'error'  => 'Clover DELETE HTTP ' . $delCode,
            'detail' => substr($delBody, 0, 300),
            'ver'    => SCRIPT_VER
        )));

    case 'create_order':
        $title = isset($_GET['title']) ? trim(urldecode($_GET['title'])) : '';
        if ($title === '') {
            http_response_code(400);
            die(json_encode(array('error' => 'Missing title', 'ver' => SCRIPT_VER)));
        }
        $coBody = json_encode(array('title' => $title));
        $ch = curl_init(BASE . '/orders');
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, $coBody);
        curl_setopt($ch, CURLOPT_HTTPHEADER, array(
            'Authorization: Bearer ' . TOKEN,
            'Content-Type: application/json',
            'Accept: application/json'
        ));
        curl_setopt($ch, CURLOPT_TIMEOUT, 20);
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 10);
        $coRespBody = curl_exec($ch);
        $coCode     = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $coErr      = curl_error($ch);
        curl_close($ch);
        if ($coErr) {
            http_response_code(502);
            die(json_encode(array('error' => 'curl: ' . $coErr, 'ver' => SCRIPT_VER)));
        }
        if ($coCode < 200 || $coCode >= 300) {
            http_response_code(502);
            die(json_encode(array(
                'error'  => 'Clover POST HTTP ' . $coCode,
                'detail' => substr($coRespBody, 0, 300),
                'ver'    => SCRIPT_VER
            )));
        }
        die($coRespBody);

    case 'add_line_item':
        $orderId = isset($_GET['order_id']) ? trim($_GET['order_id']) : '';
        $name    = isset($_GET['name'])     ? trim(urldecode($_GET['name'])) : '';
        $price   = isset($_GET['price'])    ? (int)$_GET['price']   : 0;
        $qty     = isset($_GET['qty'])      ? (int)$_GET['qty']     : 1000;
        if ($orderId === '' || $name === '') {
            http_response_code(400);
            die(json_encode(array('error' => 'Missing order_id or name', 'ver' => SCRIPT_VER)));
        }
        $liBody = json_encode(array('name' => $name, 'price' => $price, 'unitQty' => $qty));
        $ch = curl_init(BASE . '/orders/' . rawurlencode($orderId) . '/line_items');
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_POSTFIELDS, $liBody);
        curl_setopt($ch, CURLOPT_HTTPHEADER, array(
            'Authorization: Bearer ' . TOKEN,
            'Content-Type: application/json',
            'Accept: application/json'
        ));
        curl_setopt($ch, CURLOPT_TIMEOUT, 20);
        $liRespBody = curl_exec($ch);
        $liCode     = curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $liErr      = curl_error($ch);
        curl_close($ch);
        if ($liErr) {
            http_response_code(502);
            die(json_encode(array('error' => 'curl: ' . $liErr, 'ver' => SCRIPT_VER)));
        }
        if ($liCode < 200 || $liCode >= 300) {
            http_response_code(502);
            die(json_encode(array('error' => 'Clover POST HTTP ' . $liCode, 'detail' => substr($liRespBody, 0, 300), 'ver' => SCRIPT_VER)));
        }
        die($liRespBody);

    default:
        http_response_code(404);
        die(json_encode(array('error' => 'Unknown action: ' . $action, 'ver' => SCRIPT_VER)));
}
