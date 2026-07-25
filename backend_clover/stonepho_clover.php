<?php
/**
 * StonePhoApp – Clover standalone proxy
 *
 * Upload file này lên server tại BẤT KỲ đường dẫn nào, ví dụ:
 *   /loyalteapp/backend/stonepho_clover.php
 *
 * Truy cập test:
 *   https://www.stonephovaldosta.com/loyalteapp/backend/stonepho_clover.php?action=ping&key=StonePhoClover@2024
 *   https://www.stonephovaldosta.com/loyalteapp/backend/stonepho_clover.php?action=orders&key=StonePhoClover@2024
 */

header('Content-Type: application/json; charset=utf-8');
header('Access-Control-Allow-Origin: *');

// ── Auth ──────────────────────────────────────────────────────────────────────
define('API_KEY', 'StonePhoClover@2024');

function get_sent_key(): string {
    // Header Authorization (nhiều cách Apache expose)
    $h = $_SERVER['HTTP_AUTHORIZATION']
      ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION']
      ?? '';
    if (!$h && function_exists('apache_request_headers')) {
        $all = apache_request_headers();
        $h   = $all['Authorization'] ?? $all['authorization'] ?? '';
    }
    $fromHeader = trim(str_replace('Bearer ', '', $h));
    // Fallback: query param ?key=...  (dùng để test từ browser)
    return $fromHeader ?: ($_GET['key'] ?? '');
}

if (get_sent_key() !== API_KEY) {
    http_response_code(401);
    exit(json_encode(['error' => 'Unauthorized']));
}

// ── Clover credentials ────────────────────────────────────────────────────────
define('MID',   '04VMDMMGF5K81');
define('TOKEN', '10eeb58d-f5be-e989-2f6d-53363561948a');
define('BASE',  'https://api.clover.com/v3/merchants/' . MID);

function clover(string $path): string {
    $ch = curl_init(BASE . $path);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER     => ['Authorization: Bearer ' . TOKEN, 'Accept: application/json'],
        CURLOPT_TIMEOUT        => 15,
    ]);
    $body = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $err  = curl_error($ch);
    curl_close($ch);

    if ($err)           { http_response_code(502); exit(json_encode(['error' => $err])); }
    if ($code < 200 || $code >= 300) {
        http_response_code(502);
        exit(json_encode(['error' => "Clover HTTP $code", 'body' => $body]));
    }
    return $body;
}

// ── Action routing (?action=orders / ping / tables) ───────────────────────────
$action = $_GET['action'] ?? 'orders';

switch ($action) {

    case 'ping':
        echo json_encode([
            'ok'      => true,
            'server'  => 'stonepho_clover.php',
            'mid'     => MID,
            'token'   => '...' . substr(TOKEN, -6),
            'time'    => date('Y-m-d H:i:s T'),
        ]);
        break;

    case 'orders':
        // Lấy orders không filter state — Clover Dining dùng nhiều state khác nhau
        // Lọc theo thời gian: 12 giờ gần nhất, bỏ qua paid/deleted client-side
        $since = (time() - 43200) * 1000; // 12 giờ, milliseconds
        echo clover(
            '/orders?orderBy=createdTime+DESC'
            . '&expand=lineItems%2ClineItems.item%2CorderType'
            . '&limit=100'
        );
        break;

    // Debug: xem raw orders + state để biết đang dùng state gì
    case 'debug':
        $since = (time() - 43200) * 1000;
        echo clover(
            '/orders?orderBy=createdTime+DESC&limit=20'
        );
        break;

    case 'tables':
        echo clover('/tables?limit=200');
        break;

    default:
        http_response_code(404);
        echo json_encode(['error' => "Unknown action: $action"]);
}
