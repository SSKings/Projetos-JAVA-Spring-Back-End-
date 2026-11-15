import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { TERMS_VERSION, company, isCompanyConfigured } from '../../config/company';

/*
 * MODELOS de documentos legais, escritos a partir do que o sistema realmente faz. Antes de publicar:
 * revise com advogado, preencha os dados da empresa (VITE_COMPANY_*) e ajuste frete, prazos e operadores.
 */

function LegalLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <article className="max-w-3xl mx-auto px-4 sm:px-6 py-10 text-sm text-gray-700 leading-relaxed space-y-4">
      {!isCompanyConfigured() && (
        <p role="note" className="bg-amber-50 text-amber-800 border border-amber-200 rounded-lg px-4 py-3">
          Modelo ainda não preenchido: os dados entre colchetes precisam ser configurados e o texto revisado por um advogado
          antes da publicação.
        </p>
      )}
      <h1 className="text-2xl font-bold text-gray-900">{title}</h1>
      <p className="text-xs text-gray-400">Versão {TERMS_VERSION} · {company.legalName} — CNPJ {company.cnpj}</p>
      {children}
    </article>
  );
}

const H2 = ({ children }: { children: ReactNode }) => <h2 className="text-lg font-semibold text-gray-900 pt-2">{children}</h2>;

export function PrivacyPolicyPage() {
  return (
    <LegalLayout title="Política de privacidade">
      <p>
        Esta política explica como {company.legalName} ("{company.tradeName}") trata dados pessoais na loja, conforme a
        Lei Geral de Proteção de Dados (Lei 13.709/2018 — LGPD).
      </p>

      <H2>1. Dados que coletamos</H2>
      <ul className="list-disc pl-5 space-y-1">
        <li><strong>Cadastro:</strong> nome, email, telefone (opcional) e senha (guardada apenas como hash).</li>
        <li><strong>Entrega:</strong> endereços que você cadastra.</li>
        <li><strong>Compras:</strong> pedidos, itens, valores, status e dados do pagamento Pix (identificador da cobrança e da transação).</li>
        <li><strong>Segurança:</strong> registros de acesso (data, hora, IP) e de ações na conta.</li>
      </ul>

      <H2>2. Para que usamos e com qual base legal</H2>
      <ul className="list-disc pl-5 space-y-1">
        <li>Criar a conta, processar pedidos, pagamentos, entregas e atendimento — <em>execução de contrato</em> (art. 7º, V).</li>
        <li>Emitir documentos fiscais e guardar registros exigidos por lei — <em>obrigação legal</em> (art. 7º, II).</li>
        <li>Prevenir fraudes e proteger a conta (ex.: bloqueio após tentativas de login) — <em>legítimo interesse</em> e
          <em> prevenção à fraude</em> (art. 7º, IX e art. 11, II, g).</li>
        <li>Defesa em processos — <em>exercício regular de direitos</em> (art. 7º, VI).</li>
      </ul>
      <p>Não usamos seus dados para marketing nem os vendemos. Se isso mudar, pediremos seu consentimento antes.</p>

      <H2>3. Com quem compartilhamos</H2>
      <p>
        Apenas com quem é necessário para a operação: provedor de hospedagem, instituição de pagamento (Pix), transportadora
        e órgãos públicos quando exigido por lei. Esses operadores tratam os dados só para essas finalidades.
      </p>

      <H2>4. Por quanto tempo guardamos</H2>
      <ul className="list-disc pl-5 space-y-1">
        <li>Dados da conta: enquanto a conta existir.</li>
        <li>Pedidos e pagamentos: pelo prazo legal fiscal e de defesa do consumidor (em geral, 5 anos), mesmo após a exclusão da conta — nesse caso, desvinculados do seu nome e email.</li>
        <li>Registros de acesso: no mínimo 6 meses (Marco Civil da Internet, art. 15).</li>
      </ul>

      <H2>5. Seus direitos (LGPD art. 18)</H2>
      <p>
        Em <Link to="/privacy" className="text-indigo-600 hover:underline">Minha conta → Dados e privacidade</Link> você pode,
        a qualquer momento e sem custo: ver e corrigir seus dados, baixar uma cópia completa (portabilidade) e excluir sua
        conta. Para outros pedidos — informação sobre compartilhamento, revisão de decisões, reclamações — escreva para o
        encarregado: <strong>{company.dpoEmail}</strong>. Você também pode reclamar à ANPD (www.gov.br/anpd).
      </p>

      <H2>6. Segurança</H2>
      <p>
        Usamos conexão criptografada (HTTPS), senhas com hash, verificação em duas etapas opcional (obrigatória para
        administradores), bloqueio contra tentativas repetidas, controle de acesso por perfil e trilha de auditoria. Em caso de
        incidente de segurança com risco relevante, comunicaremos a ANPD e os titulares afetados.
      </p>

      <H2>7. Armazenamento no navegador</H2>
      <p>
        Guardamos no seu navegador apenas o necessário para manter você conectado (token de sessão). Não usamos cookies de
        publicidade nem ferramentas de rastreamento.
      </p>

      <H2>8. Contato e atualizações</H2>
      <p>
        Controlador: {company.legalName}, CNPJ {company.cnpj}, {company.address}. Encarregado: {company.dpoEmail}.
        Mudanças relevantes nesta política serão avisadas no site e, quando necessário, pediremos novo aceite.
      </p>
    </LegalLayout>
  );
}

export function TermsPage() {
  return (
    <LegalLayout title="Termos de uso">
      <H2>1. Quem somos</H2>
      <p>
        A loja {company.tradeName} é operada por {company.legalName}, CNPJ {company.cnpj}, {company.address}.
        Atendimento: {company.email} · {company.phone}.
      </p>

      <H2>2. Conta</H2>
      <p>
        Você é responsável pela veracidade dos dados e pelo sigilo da senha. Recomendamos ativar a verificação em duas
        etapas. Após 5 tentativas de login sem sucesso, a conta é bloqueada por 15 minutos para sua proteção.
      </p>

      <H2>3. Pedidos e preços</H2>
      <p>
        Antes de confirmar, você vê o resumo do pedido com itens, quantidades, endereço e valor total. O preço válido é o
        exibido no momento da confirmação. A disponibilidade depende do estoque; se faltar, o pedido não é concluído e nada
        é cobrado.
      </p>

      <H2>4. Pagamento (Pix)</H2>
      <p>
        O pagamento é feito por Pix (QR Code ou copia e cola) com o valor exato do pedido. O código vale pelo prazo indicado
        na tela; sem pagamento nesse prazo, o pedido é cancelado automaticamente e os itens voltam ao estoque.
      </p>

      <H2>5. Cancelamento e arrependimento</H2>
      <p>
        Você pode cancelar o pedido em <Link to="/orders" className="text-indigo-600 hover:underline">Meus pedidos</Link> enquanto
        ele não for enviado. Depois da entrega, você tem <strong>7 dias</strong> para desistir da compra sem precisar justificar
        (Código de Defesa do Consumidor, art. 49). Veja a <Link to="/trocas-e-devolucoes" className="text-indigo-600 hover:underline">
        política de trocas e devoluções</Link>.
      </p>

      <H2>6. Entrega</H2>
      <p>[Preencher: regiões atendidas, prazos, valores de frete e como acompanhar a entrega.]</p>

      <H2>7. Privacidade</H2>
      <p>O tratamento de dados pessoais segue a <Link to="/privacidade" className="text-indigo-600 hover:underline">Política de privacidade</Link>.</p>

      <H2>8. Disposições gerais</H2>
      <p>
        Estes termos seguem a legislação brasileira, em especial o Código de Defesa do Consumidor. Fica eleito o foro do
        domicílio do consumidor.
      </p>
    </LegalLayout>
  );
}

export function ReturnsPolicyPage() {
  return (
    <LegalLayout title="Trocas e devoluções">
      <H2>Direito de arrependimento (7 dias)</H2>
      <p>
        Compras feitas pela internet podem ser desistidas em até <strong>7 dias corridos</strong> a partir do recebimento,
        sem justificativa (CDC, art. 49). Em <Link to="/orders" className="text-indigo-600 hover:underline">Meus pedidos</Link>,
        abra o pedido entregue e clique em <strong>Solicitar devolução</strong>. Você receberá as instruções para devolver o
        produto; os custos de envio da devolução são da loja.
      </p>

      <H2>Reembolso</H2>
      <p>
        O valor pago é devolvido integralmente, por Pix, para a conta de origem, assim que a devolução for registrada.
        Se o pedido for cancelado antes do envio, o estorno é feito da mesma forma.
      </p>

      <H2>Produto com defeito</H2>
      <p>
        Você pode reclamar de defeitos em até 30 dias (produtos não duráveis) ou 90 dias (duráveis), contados da entrega ou,
        se o defeito for oculto, de quando ele aparecer (CDC, art. 26). Fale com {company.email}.
      </p>
    </LegalLayout>
  );
}
