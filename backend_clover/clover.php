<?php
// controllers/clover.php
// Upload file này vào: /loyalteapp/backend/controllers/clover.php
// Sau đó thêm case 'clover' vào index.php (xem cuối file)

require_once __DIR__ . '/../config.php';
require_once __DIR__ . '/../helpers.php';

auth_required(); // Dùng lại auth của LoyalteApp để bảo vệ endpoint

// ─── Cấu hình Clover ─────────────────────────────────────────────────────────
// Điền merchant_id và api_token của nhà hàng vào đây
// Hoặc tốt hơn: thêm vào config.php
if (!defined('CLOVER_MERCHANT_ID')) define('CLOVER_MERCHANT_ID', getenv('CLOVER_MERCHANT_ID') ?: '');
if (!defined('CLOVER_API_TOKEN'))   define('CLOVER_API_TOKEN',   getenv('CLOVER_API_TOKEN')   ?: '');
define('CLOVER_API_BASE', 'https://api.clover.com/v3/merchants/' . CLOVER_MERCHANT_ID);

// ─── Helper: gọi Clover API ──────────────────────────────────────────────────
function clover_get(string $path): array {
    $url = CLOVER_API_BASE . $path;
    $ch  = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_RETURNTRANSFER => true,
        CURLOPT_HTTPHEADER     => ['Authorization: Bearer ' . CLOVER_API_TOKEN, 'Accept: application/json'],
        CURLOPT_TIMEOUT        => 15,
    ]);
    $body = curl_exec($ch);
    $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    curl_close($ch);
    if ($code < 200 || $code >= 300) {
        http_response_code(502);
        die(json_encode(['error' => "Clover API error $code", 'detail' => $body]));
    }
    return json_decode($body, true) ?? [];
}

// ─── Routing ──────────────────────────────────────────────────────────────────
// $method, $id, $sub được set bởi index.php
switch ($_SERVER['REQUEST_METHOD']) {

    case 'GET':
        switch ($sub ?? $id ?? '') {

            // GET /api/clover/orders — danh sách order đang mở + line items
            case 'orders':
            case '':
                $data = clover_get('/orders?filter=state%3Dopen&expand=lineItems%2ClineItems.item%2CorderType&limit=100');
                echo json_encode($data);
                break;

            // GET /api/clover/orders/{orderId} — chi tiết 1 order
            case (isset($id) && $sub === 'orders' ? $sub : null):
                $data = clover_get("/orders/$id?expand=lineItems%2ClineItems.item");
                echo json_encode($data);
                break;

            // GET /api/clover/tables — danh sách bàn
            case 'tables':
                $data = clover_get('/tables?limit=200');
                echo json_encode($data);
                break;

            default:
                http_response_code(404);
                echo json_encode(['error' => 'Not found']);
        }
        break;

    // POST /api/clover/webhook — nhận event từ Clover (webhook)
    case 'POST':
        if (($id ?? '') === 'webhook') {
            $payload = json_decode(file_get_contents('php://input'), true);
            // Clover gửi: {"type":"CREATE","appId":"...","merchants":{"elements":[{"id":"MID"}]}}
            // Có thể log hoặc xử lý ở đây nếu cần
            http_response_code(200);
            echo json_encode(['ok' => true]);
        } else {
            http_response_code(405);
            echo json_encode(['error' => 'Method not allowed']);
        }
        break;

    default:
        http_response_code(405);
        echo json_encode(['error' => 'Method not allowed']);
}

/*
 * ─── Thêm vào index.php ────────────────────────────────────────────────────
 * Tìm đoạn switch ($resource) trong index.php và thêm case này:
 *
 *   case 'clover':
 *       require __DIR__ . '/controllers/clover.php';
 *       break;
 *
 * ─── Thêm vào config.php ───────────────────────────────────────────────────
 * define('CLOVER_MERCHANT_ID', 'YOUR_MERCHANT_ID_HERE');
 * define('CLOVER_API_TOKEN',   'YOUR_API_TOKEN_HERE');
 */
