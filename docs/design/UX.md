# DriverFin - UX (comportamento e fluxos)

Idioma: português do Brasil. Visual (cores, fontes, tamanhos): ver `docs/design/UI.md`.
Público: motoristas de aplicativo, pessoas comuns, algumas com baixa visão, usando teclado e mouse.
Nomes: o interface já usa "Novo Turno" e "Editar Turno" (T maiúsculo). Este documento segue isso em todo lugar.

Decisões do dono (já tomadas, valem para tudo abaixo):
1. Dinheiro com **máscara de centavos** digitando; **colar só dígitos = reais inteiros** (mitigação: `150` colado vira `150,00`; ver seção 5).
2. Enter avança de campo e **salva no último campo de valor** (`costFoodOtherField`); Ctrl+Enter salva de qualquer lugar.
3. **KM inicial sugerido:** o novo turno vem com o KM final do último turno.
4. Escopo: entrada de dados + S-1 a S-4.
Premissas mantidas: dois turnos no mesmo dia são permitidos; KM final igual ao inicial é permitido; hora de fim igual à de início só avisa.

---

## Entrada de dados do turno

### 1. Usuário e objetivo
O motorista, no fim do dia, registra o turno (data, horas, KM, ganhos, custos) no menor tempo possível, só com o teclado se quiser, e sem errar.

### 2. Fluxo
1. Dashboard: clica em `btnNewRecord` ("Novo Turno"), aperta Ctrl+N, ou usa Editar numa linha.
2. O formulário abre. Foco em `timeStartField`. Data já vem com hoje (novo turno). KM inicial vem sugerido (seção 7).
3. Preenche campo a campo. Enter ou Tab avança. O bloco "CÁLCULO EM TEMPO REAL" atualiza a cada tecla.
4. Ao sair de cada campo, o valor é arrumado (ex.: `8` vira `08:00`) e, se estiver errado, aparece uma mensagem curta embaixo do campo.
5. Enter no último campo de valor (`costFoodOtherField`) salva. Em `notesArea`, Enter é quebra de linha; para salvar use Tab até `btnSave` ou Ctrl+Enter.
6. Salvar com erro: a janela NÃO fecha, o foco vai para o primeiro campo com erro, que fica com a mensagem embaixo. Sem diálogo de erro de validação.
7. Salvar certo: janela fecha, dashboard atualiza, a linha salva fica selecionada na tabela.
8. Cancelar / Esc: fecha sem salvar. Se algum campo foi alterado, pergunta "Descartar alterações?" (Descartar / Continuar editando). Se nada foi alterado, fecha direto.

Falha técnica ao gravar (banco): continua com diálogo (é erro do sistema, não do campo).

### 3. Tela: formulário de turno (janela modal, já existente)
Ordem de importância: horários, KM, ganhos, custos, cálculo, salvar. Data e Observações são secundárias.

**Layout decidido (atualizado):** o conteúdo do formulário **rola**; o cálculo em tempo real e os avisos ficam numa **faixa FIXA** logo acima dos botões, sempre visível, e os botões Cancelar/Salvar também são fixos. A **data fica na mesma linha dos horários**. Why: em tela pequena o motorista não precisa rolar para ver o lucro nem para achar o botão Salvar.

```
+------------------------------------------------------+
| Novo Turno                                           |
| Preencha os dados do seu turno.                      |
| +--------------------------------------------------+ |
| | (área que rola)                                  | |
| | Data do turno *   Hora de início *  Hora de fim *| |
| | [datePicker]      [timeStartField]  [timeEndField]|
| |  dateError         timeStartError    timeEndError | |
| |                                                  | |
| | KM inicial *               KM final *            | |
| | [kmStartField] km          [kmEndField] km       | |
| |  kmStartHint / kmStartError kmEndError           | |
| |                                                  | |
| | VALORES                                          | |
| | moneyHint: Digite os centavos: 15000 = R$ 150,00 | |
| | Ganhos Uber                Ganhos 99             | |
| | R$ [earnUberField]         R$ [earn99Field]      | |
| |  earnUberMsg                earn99Msg            | |
| | Outros ganhos              Combustível           | |
| | R$ [earnOthersField]       R$ [costFuelField]    | |
| |  earnOthersMsg              costFuelMsg          | |
| | Alimentação e outros custos                      | |
| | R$ [costFoodOtherField]                          | |
| |  costFoodOtherMsg                                | |
| |                                                  | |
| | Observações (opcional)  [notesArea]  notesError  | |
| | * obrigatório                                    | |
| +--------------------------------------------------+ |
| FAIXA FIXA (sempre visível), uma linha compacta:     |
| Faturamento | Custos | Manutenção | LUCRO | Horas    |
| turnNote  /  rateWarn                                |
|                                                      |
|                  [btnCancel]  [btnSave]              |
+------------------------------------------------------+
```
Na faixa fixa, a linha tem 5 itens na ordem: Faturamento, Custos, Manutenção (rótulo completo, sem "Manut."), Lucro e Horas. **Lucro** e **Faturamento** continuam os de maior destaque. O "KM total" saiu da faixa para ela caber em uma linha. `turnNote` e `rateWarn` ficam logo abaixo da linha, também fixos, e só ocupam espaço quando têm texto.
Elementos de mensagem: cada campo tem UM rótulo logo abaixo (`<campo>Error` nos campos de hora, KM e observações; `<campo>Msg` nos de dinheiro, que mostra erro OU aviso OU dica, nessa prioridade). Ficam ocultos e sem ocupar espaço quando vazios. Prefixo "R$" e sufixo "km" são rótulos fixos fora do texto editável. Why: o texto do campo fica só com números e o motorista vê a unidade sem digitar nem apagar.
Na faixa de cálculo, o **Lucro** e o **Faturamento** são os maiores. Why: é o valor que o motorista confere para perceber se digitou algo errado (mitigação da máscara, seção 5).

### 4. Horário (Hora de início / Hora de fim)
Campo de texto com máscara ao vivo (não Spinner, não ComboBox). Why: quem digita no teclado faz "0800" em 4 toques; Spinner e ComboBox são mais lentos e piores para leitor de tela e baixa visão.

Regras ao digitar (só dígitos são aceitos; `:` é aceito e tratado como separador):
| Digitado | Mostra | Motivo |
|---|---|---|
| `0` | `0` | espera o próximo dígito |
| `08` | `08:` | dois dígitos de hora, entra o ":" sozinho |
| `0800` | `08:00` | |
| `8` | `08:` | 3 a 9 como primeiro dígito só pode ser hora de 1 dígito; completa e põe ":" |
| `830` | `08:30` | consequência da regra acima |
| `800` | `08:00` | idem |
| `1` ou `2` | `1` / `2` | espera o segundo dígito (10-19 / 20-23) |
| `24`, `29` | o `4`/`9` é ignorado (fica `2`) | hora máxima 23; depois de `2` só 0-3 |
| `08:6` | o `6` é ignorado (fica `08:`) | primeiro dígito do minuto só 0-5 |

- Máximo 4 dígitos (`HH:mm`). Um dígito a mais é ignorado.
- Backspace: apaga o último dígito e, se o ":" ficar no fim, apaga os dois juntos (`08:` vira `0`; `08:30` vira `08:3`). O ":" nunca "trava".
- Colar: pega só os dígitos e aplica as mesmas regras. `8:30`, `08:30`, `0830`, `830` viram `08:30`. Texto sem dígitos é ignorado. Mais de 4 dígitos: usa os 4 primeiros.
- Ao sair do campo (ou Enter): vazio e nunca visitado = sem mensagem (ao salvar mostra "Informe a hora de início."); `8`, `08` ou `08:` completa para `08:00`; `08:3` não é adivinhado: mensagem "Complete os minutos, por exemplo 08:30." e o foco fica no campo.
- Texto-guia: `08:00` e `17:00`. Rótulo sem "(HH:mm)".
- Saída sempre `HH:mm` com dois dígitos, que o validador atual já aceita. Nenhuma mudança no validador.
- Fim menor que início = turno atravessa a meia-noite (o modelo já soma 1 dia). Mostrar em `turnNote`: "Turno atravessa a meia-noite: {h} h." Fim igual ao início: aviso que não bloqueia: "Início e fim iguais: o turno terá 0 h."

### 5. Dinheiro: máscara de centavos
Campos: `earnUberField`, `earn99Field`, `earnOthersField`, `costFuelField`, `costFoodOtherField`.

**Como funciona (o que o motorista vê):** o campo mostra sempre um valor formatado e nunca fica em branco. Vazio = `0,00`. Ele digita só números e o valor "empurra" da direita para a esquerda:

| Teclas | Campo mostra |
|---|---|
| `1` | `0,01` |
| `1 2` | `0,12` |
| `1 2 3` | `1,23` |
| `1 2 3 4` | `12,34` |
| `1 2 3 4 5` | `123,45` |
| `1 2 3 4 5 6` | `1.234,56` (milhar ao vivo) |
| `1 5 0 0 0` | `150,00` |

Regras de teclado:
- Dígitos 0-9: entram à direita. Zero à esquerda não aparece (`0 0 5` mostra `0,05`).
- Backspace: remove o último dígito (`1.234,56` vira `123,45`). Em `0,00` não faz nada.
- Delete: zera o campo (`0,00`) em qualquer situação. Ctrl+A seguido de Backspace também zera.
- Vírgula, ponto, letras, `-`, espaço: ignorados (valor negativo não existe). Ao apertar vírgula ou ponto, `<campo>Msg` mostra a dica "A vírgula é automática. Digite só números." (some ao sair do campo ou na próxima tecla). Why: quem digita "47,50" acaba certo (4750), mas quem digita "47,5" obteria 4,75; a dica já avisa no momento.
- Teto: R$ 1.000.000,00. O dígito que faria passar do teto é ignorado (o campo não muda; `<campo>Msg` mostra "O valor máximo é R$ 1.000.000,00.").
- Ao ganhar foco (Tab, Enter ou clique): o texto inteiro fica selecionado. Se o campo está em `0,00`, o cursor fica no fim. Digitar o primeiro número substitui tudo e começa um valor novo (`150,00` selecionado + `5` = `0,05`). Why: corrigir um valor é digitar de novo; nunca acrescenta dígito ao valor antigo sem querer.
- Prefixo "R$" fixo fora do campo. Rótulos sem "(R$)".

**Risco conhecido e mitigações (digitar `150` e obter `1,50` sem perceber):**
1. `moneyHint` fixo, sempre visível acima do primeiro campo de valor: "Digite os centavos: 15000 = R$ 150,00".
2. Campo nunca vazio: mostra `0,00` desde o início, o que deixa óbvio que é máscara.
3. O valor aparece formatado no próprio campo a cada tecla e, em letra grande, no bloco CÁLCULO EM TEMPO REAL (faturamento, custos, lucro).
4. Aviso suave quando algo estiver fora do usual (não bloqueia, texto ao lado/abaixo, some sozinho quando o valor deixar de ser incomum):
   - Em qualquer campo de dinheiro com valor >= R$ 5.000,00: "Valor alto: confira se são {valor} mesmo."
   - Em `rateWarn` (abaixo do cálculo), quando as horas são > 0 e o faturamento é > 0, e o faturamento por hora é < R$ 5,00 ou > R$ 300,00: "Faturamento por hora fora do usual ({valor}/h). Confira as horas e os ganhos."
   - Aviso nunca impede salvar e não pede confirmação.

**Colar (Ctrl+V) em campo de dinheiro. Regra:**
1. Remove "R$", espaços e quebras de linha.
2. **Tem vírgula** (`1.234,56`, `1234,56`, `R$ 1.234,56`, `150,5`): valor exato. Pontos são milhar, a vírgula é o decimal, aceita 0, 1 ou 2 casas (`150,5` = 150,50). Mais de 2 casas depois da vírgula: não cola e mostra "Não entendi esse valor. Use só números e vírgula, por exemplo 150,50."
3. **Sem vírgula, só dígitos** (`123456`, `150`): são REAIS inteiros, não centavos. `123456` vira `123.456,00` e `150` vira `150,00`. Why: é a regra mais segura. Quem cola um número "puro" quase sempre copiou um valor em reais; se estivesse errado, o resultado fica 100 vezes MAIOR, o que dispara o aviso de valor alto (>= R$ 5.000) e salta aos olhos. A regra contrária (centavos) daria um valor 100 vezes MENOR, que passa despercebido. Digitar à mão continua sendo centavos; só colar é diferente, e o `moneyHint` fica visível para lembrar.
4. **Sem vírgula, com pontos**: um ponto seguido de 1 ou 2 dígitos no fim é decimal (`1234.5` = 1.234,50; `12.50` = 12,50); pontos com grupos de exatamente 3 dígitos são milhar (`1.234` = 1.234,00; `1.234.567` = 1.234.567,00, que passa do teto e cai na regra abaixo).
5. Qualquer outra coisa (letras, sinais, negativo, vazio): não cola; mostra "Não entendi esse valor. Use só números e vírgula, por exemplo 150,50."
6. Valor colado acima de R$ 1.000.000,00: não cola; mostra "O valor máximo é R$ 1.000.000,00."
7. Depois de colar, o campo mostra o valor formatado e fica com o texto selecionado.

**Como o valor sai da UI para o validador:** o campo guarda internamente um número inteiro de centavos. Para o validador, o conversor gera **texto simples com vírgula e 2 casas, sem milhar**: `1234,56`; campo em `0,00` entrega `0,00`. O validador não muda (continua rejeitando `1.000` de propósito). Edição de turno: o conversor lê o valor salvo e preenche o campo já formatado (`1.234,50`). Para o `architect`: um único conversor (centavos <-> texto exibido `1.234,56` <-> texto simples `1234,56`), com testes, usado por todos os campos de dinheiro. Também deve alimentar o cálculo em tempo real.

### 6. KM (KM inicial / KM final)
Não é dinheiro: **digitação livre**, sem máscara.
- Mostrado com milhar e **uma casa decimal**: `12.450,0`. Digitando `12450` vira `12.450,0` ao sair; `12450,5` vira `12.450,5`.
- Enquanto digita: só dígitos e UMA vírgula; o ponto digitado vira vírgula na hora; a segunda casa decimal é ignorada; letras e `-` ignorados. Sem milhar enquanto digita (o cursor não pula).
- Colar: aceita `12.450,5`, `12450,5`, `12450.5`, `12450`. Mesma regra de leitura do dinheiro com vírgula/ponto (passos 2 e 4), mas 1 casa; sem vírgula e só dígitos = KM inteiro (`12450` = 12.450,0). Ilegível: "Não entendi esse valor. Use só números e vírgula, por exemplo 12450,5."
- Sem prefixo; sufixo fixo "km".
- Convertido para texto simples (`12450,5`) antes do validador.
- Regra KM final >= KM inicial, na hora: ao sair de `kmEndField` (e ao sair de `kmStartField` se o final já estiver preenchido), se final < inicial, mensagem em `kmEndError`: "O KM final precisa ser maior ou igual ao KM inicial (12.450,0)." Mantém o valor digitado.
- KM igual: permitido, sem aviso.

### 6b. Faixa de datas permitida
- A data do turno vai de **01/01/2000** até **hoje + 1 ano** (relógio do computador). Why: bloqueia datas digitadas por engano (ex.: ano 2205) sem atrapalhar quem registra um turno um pouco à frente.
- Fora da faixa, ao sair do campo ou ao salvar, `dateError` mostra: "Data fora do permitido. Use de 01/01/2000 até um ano a partir de hoje." (`FormMessages.DATE_OUT_OF_RANGE`). O foco vai para a data como em qualquer erro de campo.
- Editando um turno cujo valor guardado em dinheiro já está fora do permitido (ex.: arquivo antigo ou alterado fora do app): o campo não é arrumado em silêncio; ao salvar mostra "O valor guardado está fora do permitido. Corrija antes de salvar." (`FormMessages.STORED_MONEY_OUT_OF_RANGE`) no campo (`<campo>Msg`) e o foco vai para ele.

### 7. KM inicial sugerido
- Só em **novo turno**. Edição de turno existente não sugere nada (mostra o valor salvo).
- Valor sugerido = KM final do "último turno". **Último turno** = o turno com a maior data; empate de data: maior hora de início; empate de hora de início: o de maior id (o gravado por último). Why: com dois turnos no mesmo dia, o odômetro mais recente é o do que começou mais tarde; o id decide o raro empate total. Vale a data de início do turno, mesmo que ele termine depois da meia-noite.
- O campo vem preenchido (formatado `12.630,0`) com o texto selecionado: digitar sobrescreve. `kmStartHint` mostra "Sugerido: KM final do último turno." e some quando o motorista altera o campo.
- Primeiro turno (não há anterior): campo vazio, sem dica.
- Consulta nova no DAO (ver tarefa T7).

### 8. Foco e teclado do formulário
- Foco inicial: `timeStartField` (novo turno) ou o primeiro campo (edição). A data já vem pronta.
- Ordem de Tab: datePicker, timeStartField, timeEndField, kmStartField, kmEndField, earnUberField, earn99Field, earnOthersField, costFuelField, costFoodOtherField, notesArea, btnCancel, btnSave.
- Enter: avança para o próximo campo, igual a Tab, em `datePicker` e em todos os campos de uma linha. Em `costFoodOtherField` salva. Em `notesArea` é quebra de linha. Em botão focado, ativa o botão. `btnSave` NÃO é "botão padrão" da janela (para o Enter não salvar sem querer enquanto o foco está em outro campo).
- Ctrl+Enter salva de qualquer lugar, inclusive em `notesArea`.
- Ao entrar em qualquer campo de texto: seleciona todo o texto.
- Esc: fecha o formulário (fluxo, passo 8).
- Alt+S "Salvar Turno", Alt+C "Cancelar" (letra sublinhada nos botões).
- Atalhos do dashboard (Ctrl+N etc.) não valem enquanto o formulário está aberto (a janela é modal).

### 9. Feedback de erro
- Mensagem curta embaixo do campo, em texto (não só borda; `input-error` do UI.md é o complemento visual). Cada mensagem diz o que está errado e o que fazer, sem culpar.
- Quando: (a) ao sair do campo, só se o campo foi tocado; (b) ao salvar, todos os campos inválidos mostram a mensagem ao mesmo tempo; (c) a mensagem some assim que o valor fica válido, enquanto digita.
- Ao salvar com erro: foco no primeiro campo inválido (ordem de Tab), com o texto selecionado. Leitor de tela lê a mensagem (campo acessível ligado à mensagem).
- Sem diálogo "Erro de Validação". `btnSave` continua habilitado (desabilitado confunde).
- Falha técnica: diálogo, como hoje.

### 10. Ajudas
| Ajuda | Decisão |
|---|---|
| Data = hoje | FAZER |
| KM inicial = KM final do último turno | FAZER (seção 7) |
| Duração no cálculo + aviso de meia-noite | FAZER |
| Aviso de valores fora do usual | FAZER (seção 5) |
| Lembrar último valor de combustível | NÃO (preço e litros mudam todo dia) |
| Aviso de turno repetido na mesma data | DEPOIS (dois turnos por dia são permitidos) |

### 11. Estados
| Estado | O que mostra |
|---|---|
| Novo turno | Data = hoje, KM inicial sugerido (se existir turno anterior), horas vazias com texto-guia, campos de dinheiro em `0,00`; cálculo mostra "—" até haver horas e KM válidos |
| Edição | Título "Editar Turno"; todos os campos preenchidos no formato exibido; KM inicial sem sugestão |
| Digitando | Cálculo atualiza a cada tecla; sem erros em campos ainda não visitados |
| Campo inválido | Mensagem embaixo + borda `input-error` |
| Valor incomum | Aviso embaixo do campo ou em `rateWarn`; nada é bloqueado |
| Salvando | `btnSave` desabilitado até terminar (evita clique duplo) |
| Sucesso | Janela fecha; dashboard atualiza e mostra "Turno salvo." (mensagem curta) |
| Erro técnico | Diálogo "Erro ao salvar" |
| Primeiro turno | KM inicial vazio, sem dica |

### 12. Textos exatos
Rótulos: "Data do turno *", "Hora de início *", "Hora de fim *", "KM inicial *", "KM final *", "Ganhos Uber", "Ganhos 99", "Outros ganhos", "Combustível", "Alimentação e outros custos", "Observações (opcional)". Legenda: "* obrigatório". Cabeçalho do bloco: "VALORES". Bloco de resultado: "CÁLCULO EM TEMPO REAL".
Prefixo/sufixo: "R$", "km". Texto-guia: horas `08:00` / `17:00`; KM `12.450,0` / `12.630,0`; dinheiro mostra `0,00`; observações "Ex.: trânsito intenso, abastecimento".
Títulos: "Novo Turno", "Editar Turno". Subtítulo: "Preencha os dados do seu turno." Botões: "Cancelar", "Salvar Turno".
`moneyHint`: "Digite os centavos: 15000 = R$ 150,00".
Dica ao digitar vírgula/ponto em dinheiro: "A vírgula é automática. Digite só números."
Dica KM: "Sugerido: KM final do último turno."
Avisos: "Turno atravessa a meia-noite: {h} h." / "Início e fim iguais: o turno terá 0 h." / "Valor alto: confira se são {valor} mesmo." / "Faturamento por hora fora do usual ({valor}/h). Confira as horas e os ganhos."
Descarte: título "Descartar alterações?", texto "Você alterou o turno e ainda não salvou.", botões "Descartar" e "Continuar editando" (padrão: Continuar editando).
Sucesso: "Turno salvo."
Erros por campo:
- Data: "Escolha a data do turno."
- Data fora da faixa: "Data fora do permitido. Use de 01/01/2000 até um ano a partir de hoje."
- Valor guardado fora do permitido (edição): "O valor guardado está fora do permitido. Corrija antes de salvar."
- Hora vazia: "Informe a hora de início." / "Informe a hora de fim."
- Hora incompleta: "Complete os minutos, por exemplo 08:30."
- Hora inválida (colada): "Hora inválida. Use de 00:00 a 23:59."
- KM vazio: "Informe o KM inicial." / "Informe o KM final."
- KM final menor: "O KM final precisa ser maior ou igual ao KM inicial ({valor})."
- Número ilegível (dinheiro): "Não entendi esse valor. Use só números e vírgula, por exemplo 150,50."
- Número ilegível (KM): "Não entendi esse valor. Use só números e vírgula, por exemplo 12450,5."
- Acima do teto: "O valor máximo é R$ 1.000.000,00."
- Observações longas: "Passou {n} caracteres do limite de 500."

### 13. Acessibilidade
- Todo campo tem rótulo ligado; mensagem ligada ao campo.
- Erro e aviso nunca só por cor: texto + borda; símbolo antes do texto fica a critério do `ui-designer`.
- Cálculo em tempo real não anuncia a cada tecla; só o lucro ao sair de campo. Valor "—" é lido como "indisponível".
- Campos de dinheiro: o leitor de tela deve ler o valor formatado completo ("um mil duzentos e trinta e quatro reais e cinquenta e seis"), não dígito a dígito, ao sair/entrar no campo.
- Fonte dos campos no mínimo igual ao corpo atual; valores do cálculo maiores.

### 14. Critérios de aceitação UX (formulário)
Horário
1. Digitar `0800` em Hora de início mostra `08:00` ao vivo, sem digitar ":".
2. Digitar `830` mostra `08:30`; digitar `8` mostra `08:`; sair do campo com `08:` ou `8` deixa `08:00`.
3. Digitar `24` (o `4` é ignorado) e minuto começando em `6` (ignorado).
4. Backspace em `08:` deixa `0`; em `08:30` deixa `08:3`.
5. Colar `8:30`, `830` e `08:30` resulta em `08:30`.
6. Sair de Hora com `08:3` mostra "Complete os minutos, por exemplo 08:30." e o foco fica no campo.
7. Fim menor que início mostra "Turno atravessa a meia-noite: {h} h." com horas corretas. Fim igual ao início mostra o aviso e deixa salvar.
Dinheiro
8. Digitar `1`,`2`,`3`,`4`,`5` mostra `0,01`, `0,12`, `1,23`, `12,34`, `123,45`; com `6` mostra `1.234,56`.
9. Digitar `15000` mostra `150,00`. Campo novo mostra `0,00`; ao receber foco o texto fica selecionado e digitar começa valor novo.
10. Backspace remove o último dígito; Delete zera; Ctrl+A e Backspace zera.
11. Digitar vírgula, ponto, letra ou `-` não muda o valor; vírgula/ponto mostra "A vírgula é automática. Digite só números."
12. Com `999.999,99`, digitar mais um dígito é ignorado; o valor não passa de `1.000.000,00`.
13. Colar `R$ 1.234,56`, `1234,56` e `1.234,56` resulta em `1.234,56`. Colar `123456` resulta em `123.456,00` (reais) e mostra aviso de valor alto. Colar `1234,567` ou `abc` não muda o campo e mostra "Não entendi esse valor...".
14. Valor >= R$ 5.000,00 em um campo mostra "Valor alto: confira se são {valor} mesmo." e permite salvar.
15. Faturamento por hora < R$ 5 ou > R$ 300 (com horas e faturamento > 0) mostra o aviso `rateWarn` e permite salvar.
16. `moneyHint` está sempre visível e o cálculo mostra faturamento e lucro em tamanho grande, atualizando a cada tecla.
17. Salvar com valores `0,00` grava R$ 0,00 sem erro; salvar `1.234,50` funciona (a tela entrega `1234,50` ao validador); editar turno mostra os valores no mesmo formato.
KM e KM sugerido
18. KM exibido como `12.450,0`; digitar `12450` e sair mostra `12.450,0`. KM final menor que o inicial mostra a mensagem ao sair do campo, sem clicar em Salvar. KM igual não mostra aviso.
19. Novo turno: KM inicial = KM final do último turno (maior data; empate: maior hora de início; empate: maior id), com texto selecionado e a dica; digitar sobrescreve e a dica some. Primeiro turno: vazio e sem dica. Editar turno: sem sugestão.
Teclado e erros
20. Novo turno: data = hoje; foco em Hora de início.
21. Entrar em qualquer campo seleciona o texto todo; Enter vai ao próximo campo; Enter em Alimentação salva; Enter em Observações quebra linha; Ctrl+Enter salva de qualquer campo.
22. Salvar com vários erros mostra uma mensagem em cada campo errado, sem diálogo, e o foco vai ao primeiro (na ordem de Tab).
23. Esc com campos alterados pergunta "Descartar alterações?" (padrão "Continuar editando"); sem alteração, fecha direto.
24. Todas as mensagens são legíveis sem depender da cor.
25. Data antes de 01/01/2000 ou depois de hoje + 1 ano mostra "Data fora do permitido. Use de 01/01/2000 até um ano a partir de hoje." e não salva.
26. Em janela baixa, o conteúdo rola, mas a faixa de cálculo (Faturamento, Custos, Manutenção, Lucro, Horas) e os botões Cancelar/Salvar continuam visíveis; a data está na mesma linha dos horários e o rótulo diz "Manutenção" por extenso.
27. Editar turno com valor de dinheiro guardado fora do permitido mostra "O valor guardado está fora do permitido. Corrija antes de salvar." e não salva até corrigir.

---

## Dashboard: S-1 a S-4

### 1. Usuário e objetivo
O motorista abre o app para saber "como foi hoje", registrar o turno e consultar o período, sem procurar nada. Primeiro uso: entender o que fazer.

### 2. Layout do `dashboard.fxml` (ordem dos filhos do VBox principal, de cima para baixo)
Mudanças em **negrito**; o resto fica como está.
1. Cabeçalho (logo, `btnBackup`, `btnPdfReport`, `btnNewRecord`): sem mudança, só tooltips e atalhos.
2. **NOVO `todayCard`** (S-1), logo abaixo do cabeçalho. Why: "hoje" vem antes do período; é a primeira coisa que o motorista procura.
3. Linha de filtros (`periodComboBox`, datas, `searchField`): sem mudança de lugar.
4. Barra de configuração da manutenção (S-4): mesma posição (abaixo dos filtros), com novo texto e uma linha de explicação.
5. GridPane de KPIs (4 colunas + linha 2): sem mudança de lugar; só o subtexto do card RESERVA MANUTENÇÃO muda (S-4).
6. Gráfico: sem mudança.
7. Tabela de turnos: **placeholder novo** e **dica de teclado** no rodapé do card.
8. **NOVO `emptyStateBox`** (S-2): mesmo nível dos filhos acima, logo abaixo do cabeçalho; só aparece quando não há nenhum turno gravado.

```
Com turnos (normal)                          Sem nenhum turno (S-2)
+---------------------------------------+    +---------------------------------------+
| cabeçalho  [Backup][PDF][Novo Turno]  |    | cabeçalho  [Backup][PDF][Novo Turno]  |
| todayCard  (S-1)                      |    |                                       |
| filtros: Período [..]   [pesquisar]   |    |          emptyStateTitle              |
| manutenção (S-4)                      |    |          emptyStateText               |
| KPIs (grid)                           |    |          [btnEmptyNew]                |
| gráfico                               |    |                                       |
| tabela + lblShortcutsHint             |    +---------------------------------------+
+---------------------------------------+
```

### 3. S-1: Resumo de hoje (`todayCard`)
Conteúdo, da esquerda para a direita:
```
+----------------------------------------------------------------+
| HOJE - terça-feira, 10 de novembro                lblTodayTitle |
| LUCRO DE HOJE        HORAS            FATURAMENTO POR HORA      |
| R$ 182,40            8,5 h            R$ 38,20 / h              |
| lblTodayProfit       lblTodayHours    lblTodayGrossPerHour      |
| lblTodayGross: "Faturamento: R$ 182,40 · 2 turnos"              |
+----------------------------------------------------------------+
```
- **Linha de resumo (uma só, atualizada):** `lblTodayGross` junta faturamento e número de turnos numa linha: "Faturamento: R$ {valor} · {N} turno(s)". Singular com 1: "Faturamento: R$ 182,40 · 1 turno". Plural com 2 ou mais: "Faturamento: R$ 182,40 · 2 turnos". Antes eram duas linhas (faturamento e "2 turnos hoje"); a contagem agora aparece sempre, também com 1 turno. Why: uma linha ocupa menos espaço e o motorista vê de uma vez quanto entrou e em quantos turnos.
- `lblTodayProfit` é o maior texto do dashboard (maior que os KPIs).
- Dados: todos os turnos cuja **data do turno = hoje** (relógio do computador), somados. Não depende do filtro de período nem da pesquisa. Lucro = o mesmo cálculo do KPI LUCRO LÍQUIDO (faturamento - custos - manutenção). Faturamento por hora = faturamento de hoje / horas de hoje. Turno que passa da meia-noite conta no dia em que começou.
- Prejuízo: o valor sai com sinal e a palavra: "-R$ 12,30 (prejuízo)". Nunca só por cor.
- Horas = 0 (turno com início = fim): `lblTodayHours` "0 h" e `lblTodayGrossPerHour` "—".
- **Quando não há turno hoje, mas existem turnos em outros dias:** o card continua visível, com título "HOJE - {data}", os três valores ficam ocultos (`managed=false`; mostrar R$ 0,00 faria parecer que o dia foi perdido ou que o app quebrou), e aparece `lblTodayEmptyText` "Você ainda não registrou turno hoje." com o botão `btnTodayNew` "Registrar turno de hoje" (abre o mesmo formulário de Novo Turno).
- **Quando não há nenhum turno gravado:** `todayCard` fica oculto (`emptyStateBox` assume).
- Quando atualiza: ao salvar, excluir, abrir o app, quando a janela volta a ficar em foco **e por um relógio a cada minuto** (cobre a virada do dia com o app aberto, mesmo que a janela fique o tempo todo em foco, por exemplo num painel do carro). Na virada da meia-noite o título passa para a nova data e o card troca para "Você ainda não registrou turno hoje." sozinho, sem o motorista fazer nada. A atualização por relógio não mexe na seleção da tabela nem no foco.
- **Falha ao carregar os turnos na abertura:** se os turnos não puderem ser lidos do banco na primeira carga, a tela mostra um quadro de erro neutro (no lugar do quadro de boas-vindas) com o título "Não foi possível carregar os turnos" e o texto "Feche e abra o aplicativo para tentar de novo.", sem botão "Novo Turno" e sem valores zerados. Não aparece o texto "você ainda não registrou nenhum turno", para não parecer que os dados foram perdidos. Se a falha ocorrer numa recarga com dados já na tela, continua o diálogo de erro. Why: o texto antigo dizia "Tente novamente", mas não há botão para isso; o que funciona é reabrir o app.
- Clicar no card não faz nada (é só informação).

### 3b. Layout atualizado do dashboard (decidido)
- **Botão `btnNewRecord` ("Novo Turno") do cabeçalho:** é de **contorno (secundário)** quando outro botão de ação já está em destaque na tela (`btnTodayNew` no card HOJE vazio, ou `btnEmptyNew` no estado vazio) e **primário** nos demais casos. Why: uma tela deve ter uma só ação principal em destaque; dois botões primários iguais disputam a atenção.
- **Tabela de turnos:** a coluna **Ações** é a primeira depois de **Data** (visível sem rolar para o lado). Há um **menu de colunas** (botão no cabeçalho da tabela) para ocultar colunas que o motorista não usa. A tabela **cresce com a janela** (ocupa o espaço que sobra na altura e na largura).

### 3c. Backup e relatório PDF: mensagens de erro
- Tanto Backup quanto PDF escrevem um arquivo escolhido pelo motorista.
- **Arquivo aberto em outro programa** (não dá para gravar): "Feche o arquivo, se estiver aberto em outro programa, e tente novamente." Vem junto de uma frase curta do que falhou ("Não foi possível salvar o backup." / "Não foi possível gerar o relatório."). Sem código técnico.
- **Destino é o próprio banco de dados** (o motorista escolheu o arquivo que o app usa): a gravação é recusada antes de começar, com "Escolha outro arquivo: este é o banco de dados que o aplicativo está usando." Why: gravar por cima do banco apagaria todos os turnos.
- Depois de qualquer recusa o seletor de arquivo não reabre sozinho; o motorista repete a ação se quiser.

### 4. S-2: Estado vazio acolhedor (`emptyStateBox`)
- Quando: não existe nenhum turno no banco (não confundir com "período sem turnos").
- Mostra no centro: `emptyStateTitle` "Bem-vindo ao DriverFin", `emptyStateText` "Você ainda não registrou nenhum turno. Comece pelo botão Novo Turno.", `btnEmptyNew` "Novo Turno" (botão primário, o foco inicial da janela, abre o formulário).
- Ficam ocultos (`visible=false`, `managed=false`): `todayCard`, linha de filtros, barra de manutenção, KPIs, gráfico e tabela. O cabeçalho continua igual (Backup, PDF e Novo Turno no topo não mudam). Why: gráfico e valores zerados no primeiro uso parecem tela quebrada.
- Ao salvar o primeiro turno: `emptyStateBox` some e tudo aparece, com a linha salva selecionada.
- **Período ou pesquisa sem resultado (há turnos no banco):** tela normal; KPIs com R$ 0,00; `lblChartEmpty` "Sem dados para o período" (já existe); a tabela mostra o texto-guia do `tableRecords.placeholder`: "Nenhum turno neste período." ou, se há texto em `searchField`, "Nenhum turno encontrado para "{texto}"."

### 5. S-3: Atalhos de teclado do dashboard
| Atalho | Ação | Onde vale |
|---|---|---|
| Ctrl+N | Novo Turno (`handleNewRecord`) | em qualquer lugar da janela |
| Ctrl+B | Backup (`handleBackup`) | em qualquer lugar |
| Ctrl+P | Relatório PDF (`handleExportPdf`) | em qualquer lugar |
| Ctrl+F | Foca `searchField` e seleciona o texto | em qualquer lugar |
| Esc | Em `searchField` com texto: apaga o texto; vazio: devolve o foco à tabela | só em `searchField` |
| Enter | Edita o turno selecionado | só com `tableRecords` em foco |
| Delete | Exclui o turno selecionado, com confirmação | só com `tableRecords` em foco |

- **Conflito com campos de texto:** Ctrl+N, Ctrl+B, Ctrl+P e Ctrl+F não têm função nos campos de texto do JavaFX (só Ctrl+A/C/V/X/Z/Y), então podem ser atalhos da janela. **Delete e Enter só valem com a tabela em foco**, porque em campo de texto Delete apaga um caractere e Enter é de campo. Os atalhos valem também enquanto `searchField` está em foco, exceto Delete e Enter.
- **F5 não será usado.** Why: os dados só mudam por dentro do app e a tela já atualiza ao salvar/excluir; um F5 não teria o que fazer. Não conflita com campos de texto, então pode ser adicionado depois se algo mudar.
- Ctrl+N/B/P não fazem nada enquanto um diálogo ou o formulário (modal) estão abertos.
- Em estado vazio: Ctrl+N funciona; Ctrl+F, Enter e Delete não fazem nada (nada visível).
- Confirmação de exclusão (reaproveitar a que já existe no botão de Ações; se não houver, criar): título "Excluir turno?", texto "O turno de {dd/MM/yyyy} (lucro {valor}) será excluído e não dá para desfazer.", botões "Excluir" e "Cancelar" (padrão: Cancelar). Depois de excluir, a seleção vai para a linha seguinte (ou anterior) e o foco fica na tabela; mensagem "Turno excluído."
- Descoberta: tooltips "Novo Turno (Ctrl+N)", "Backup (Ctrl+B)", "Relatório PDF (Ctrl+P)"; `searchField` com texto-guia "Pesquisar por notas, data ou valor... (Ctrl+F)"; e `lblShortcutsHint` no rodapé do card Turnos: "Dicas: Enter edita o turno selecionado, Delete exclui. Ctrl+N cria um novo turno."
- Ordem de Tab do dashboard: `btnBackup`, `btnPdfReport`, `btnNewRecord`, `btnTodayNew` (se visível), `periodComboBox`, datas (se visíveis), `searchField`, `maintRateField`, `tableRecords`, botões de Ações das linhas.
- Foco inicial: `btnEmptyNew` no estado vazio; senão `tableRecords`.

### 6. S-4: Taxa de manutenção explicada
Barra abaixo dos filtros, mesmo card, duas linhas:
```
+----------------------------------------------------------------+
| Reserva de manutenção:  R$ [maintRateField] por km             |
| lblMaintHelp: Dinheiro que você separa a cada km rodado para   |
| pneus, óleo e revisões. Ele é descontado do lucro.             |
+----------------------------------------------------------------+
```
- Substitui "⚙️ Taxa Manutenção:" e "R$ / KM". `maintRateField` continua campo de texto livre (não é máscara: é um valor pequeno por km, como `0,25`), com prefixo fixo "R$" e sufixo "por km"; o valor padrão sugerido na primeira abertura continua `0,25` (já é o valor atual do campo). `lblMaintHelp` é sempre visível (não é tooltip: tooltip não aparece para quem usa só teclado).
- Ao sair do campo: formata com 2 casas (`0,3` vira `0,30`). Aceita vírgula; ponto vira vírgula. Vazio ou ilegível volta ao último valor válido e mostra a mensagem curta "Valor inválido. Use por exemplo 0,25." ao lado do campo, sem diálogo. Mudar o valor recalcula KPIs, tabela e `todayCard`.
- Card RESERVA MANUTENÇÃO: subtexto passa de "Reserva estimada para pneus, óleo etc." para dinâmico "R$ {taxa} por km rodado, para pneus, óleo e revisões." (ex.: "R$ 0,25 por km rodado, para pneus, óleo e revisões.").
- Título do card continua "RESERVA MANUTENÇÃO" (mesmo nome em todo lugar: "Reserva de manutenção" no texto corrido, em maiúsculas no card; no formulário a coluna da tabela e o cálculo usam "Manut."/"Manutenção" como hoje).

### 7. Estados do dashboard
| Estado | O que mostra |
|---|---|
| Nenhum turno gravado | `emptyStateBox` apenas (seção 4) |
| Há turnos, nenhum hoje | `todayCard` com "Você ainda não registrou turno hoje." + `btnTodayNew`; resto normal |
| Há turno hoje | `todayCard` com valores |
| Período/pesquisa sem resultado | Tela normal, KPIs zerados, placeholder da tabela e do gráfico |
| Turno salvo | "Turno salvo." (mensagem curta), linha selecionada |
| Turno excluído | "Turno excluído." |
| Taxa inválida | Mensagem ao lado de `maintRateField`, volta ao último valor |

### 8. Critérios de aceitação UX (dashboard)
1. Com o banco vazio, só o cabeçalho e `emptyStateBox` aparecem, com o texto "Você ainda não registrou nenhum turno. Comece pelo botão Novo Turno." e `btnEmptyNew` focado; Enter nele abre o formulário.
2. Salvar o primeiro turno faz `emptyStateBox` sumir e o dashboard completo aparecer.
3. Com turno hoje, `todayCard` aparece logo abaixo do cabeçalho com lucro (maior texto da tela), horas e faturamento por hora, e uma linha "Faturamento: R$ {valor} · 1 turno"; com dois turnos hoje mostra a soma e "Faturamento: R$ {valor} · 2 turnos".
4. Sem turno hoje (mas com outros dias), `todayCard` mostra "Você ainda não registrou turno hoje." e "Registrar turno de hoje", sem valores zerados; o botão abre o formulário.
5. `todayCard` não muda ao trocar o período nem ao pesquisar; muda ao salvar, excluir e ao alterar a taxa de manutenção.
6. Lucro negativo de hoje aparece como "-R$ 12,30 (prejuízo)".
7. Ctrl+N abre Novo Turno, Ctrl+B faz backup, Ctrl+P exporta PDF, Ctrl+F foca a pesquisa com o texto selecionado; todos funcionam com foco em qualquer campo da tela, e digitar nos campos não é afetado.
8. Com a tabela em foco, Enter abre Editar Turno do selecionado e Delete pede "Excluir turno?" (padrão Cancelar). Em `searchField`, Delete apaga caractere e Enter não edita nada.
9. Esc em `searchField` com texto apaga o texto; sem texto, volta o foco à tabela.
10. A barra de manutenção mostra "Reserva de manutenção: R$ [0,25] por km" e a explicação sempre visível; sair do campo com `0,3` mostra `0,30`; valor ilegível volta ao anterior com a mensagem, sem diálogo.
11. O card RESERVA MANUTENÇÃO mostra "R$ 0,25 por km rodado, para pneus, óleo e revisões." com a taxa atual.
12. Período sem turnos: tabela mostra "Nenhum turno neste período."; pesquisa sem resultado mostra "Nenhum turno encontrado para "{texto}"."
13. Nenhum estado depende só de cor (prejuízo tem texto, erros têm texto).
14. Com o app aberto e em foco na virada do dia (ou até 1 minuto depois), o título do `todayCard` muda para a nova data e o card passa a "Você ainda não registrou turno hoje.", sem clicar em nada.
15. Se o banco não puder ser lido ao abrir, a tela mostra o quadro "Não foi possível carregar os turnos / Feche e abra o aplicativo para tentar de novo.", sem o texto de boas-vindas, sem botão "Novo Turno" e sem valores zerados.
16. Backup ou PDF para um arquivo aberto em outro programa mostra "Feche o arquivo, se estiver aberto em outro programa, e tente novamente."; escolher o próprio banco de dados como destino mostra "Escolha outro arquivo: este é o banco de dados que o aplicativo está usando." e nada é gravado.
17. Na tabela, a coluna Ações está logo depois de Data e visível sem rolar; o menu de colunas oculta e mostra colunas; a tabela cresce ao aumentar a janela.
18. Com `btnTodayNew` ou `btnEmptyNew` visível, `btnNewRecord` do cabeçalho aparece como botão de contorno; nos demais casos, como primário.

---

## Pendências de UX para depois

| # | O quê | Esforço | Prior. |
|---|---|---|---|
| S-1 a S-4 | Feitas (detalhadas acima) | P | Feitas |
| S-5 | Tela pequena: formulário e tabela já rolam/crescem; falta testar e ajustar em 1366x768 e janelas ainda menores | M | M |
| S-6 | Tamanho de letra maior: Normal / Grande / Muito grande (primeira da fila) | M | A |
| S-7 | Comparação com o período anterior nos KPIs (texto e seta, não só cor) | M | M |
| S-8 | Meta diária (ou semanal) de lucro com barra de progresso | M | B |
| S-9 | Compactar a barra da taxa de manutenção na mesma linha dos filtros (hoje fica numa linha abaixo, com explicação) | P | B |
| S-10 | Desabilitar Backup e PDF quando não há dados (hoje o botão funciona e gera arquivo vazio) | P | M |
| S-11 | Mostrar o lucro na mensagem "Turno salvo." (ex.: "Turno salvo. Lucro: R$ 182,40.") | P | B |

Limitação conhecida: o JavaFX não permite itálico só no texto-guia (placeholder) dos campos; a diferença entre texto-guia e texto digitado fica só na **cor**. Por isso o texto-guia nunca é a única informação (todo campo tem rótulo visível acima) e a máscara de dinheiro mostra `0,00` como valor real, não como guia.

Ideias para depois: avisar KM total do turno fora do usual; avisar turno repetido na mesma data; leitor de tela com prévia do valor da máscara.

---

## Tarefas sugeridas

Ordem pensada para entregar pequeno. "Paralelo" = pode rodar ao mesmo tempo que as tarefas indicadas, sem tocar os mesmos arquivos. Nomes de classes são sugestão para o `architect`.

| ID | Tarefa | Arquivos prováveis | Critério de aceite | Paralelo com |
|---|---|---|---|---|
| T1 | (a) Máscara de horário: classe pura que filtra digitação e colar, formata `HH:mm`, completa ao sair | novo `util/TimeMask.java` + `TimeMaskTest.java` | Critérios 1-5 (horário) passam em testes unitários, incluindo `8`, `830`, `24`, `08:6`, colar `8:30`, Backspace | T2, T3, T7a, T9, T11 |
| T2 | (b) Dinheiro: máscara de centavos + conversor único (centavos <-> `1.234,56` <-> `1234,56`), regra de colar, teto | novo `util/MoneyMask.java` (ou `MoneyText.java`) + teste; usar/ajustar `CurrencyFormat.java` só se necessário | Critérios 8-13 e 17 passam em testes: sequência `1..6`, `15000`, Backspace, Delete, teto, colar `123456` = 123.456,00, `1234,567` rejeitado; saída `1234,56` aceita pelo `WorkShiftValidator` sem alterá-lo | T1, T3, T7a, T9, T11 |
| T3 | (c) KM com milhar e 1 casa: filtro de digitação, formatação `12.450,0`, colar, texto simples | novo `util/KmMask.java` + teste | `12450` formata `12.450,0`; `12450.5` vira `12450,5`; 2ª casa ignorada; saída `12450,5` aceita pelo validador | T1, T2, T7a, T9, T11 |
| T4 | Ligar T1-T3 ao formulário: prefixo "R$"/sufixo "km", `moneyHint`, selecionar tudo ao focar (cursor no fim se `0,00`), cálculo em tempo real usando o conversor, valores grandes | `RecordFormController.java`, `record-form.fxml` | Critérios 1, 8, 9, 16, 18 (formulário) e a conversão para o validador funcionam usando a tela | T9 (depois de T1-T3) |
| T5 | (d) Erros por campo: rótulos `<campo>Error/Msg`, mensagem ao sair do campo e ao salvar, foco no primeiro inválido, sem diálogo de validação, regra KM final >= inicial na hora | `RecordFormController.java`, `record-form.fxml`, textos em `Dialogs`/constantes se existirem | Critérios 6, 18, 22, 24 | T9 (depois de T4) |
| T6 | (e) Enter avança, Enter salva em `costFoodOtherField`, Ctrl+Enter salva, Esc com confirmação de descarte, Alt+S/Alt+C, ordem de Tab | `RecordFormController.java`, `record-form.fxml`, `Dialogs.java` | Critérios 21 e 23 | T9, T7a (depois de T5) |
| T7a | (f) Consulta do último turno no DAO (data desc, hora início desc, id desc) | `WorkShiftDAO.java` + teste em `src/test/.../dao` | Teste com dois turnos no mesmo dia devolve o de maior hora de início; empate total devolve o de maior id; banco vazio devolve vazio | T1, T2, T3, T9, T11 |
| T7b | (f) KM inicial sugerido e `kmStartHint` no formulário (só novo turno, texto selecionado, dica some ao editar) | `RecordFormController.java`, `record-form.fxml` | Critério 19 | T9 (depois de T6 e T7a) |
| T8 | (g) Aviso de meia-noite / início=fim em `turnNote`, avisos de valor alto por campo e `rateWarn` | `RecordFormController.java`, `record-form.fxml` (ou `ShiftMetrics.java` para a regra de R$/h) | Critérios 7 e 14, 15 | T9 (depois de T7b) |
| T9 | (h) S-1 e S-2: `todayCard`, `emptyStateBox`, placeholder da tabela, regra "hoje" (soma de turnos da data) | `DashboardController.java`, `dashboard.fxml`, `SummaryService.java` (+ teste) | Critérios 1-6 e 12 do dashboard | T1-T3, T7a, T11 (não com T10) |
| T10 | (h) S-3 e S-4: atalhos Ctrl+N/B/P/F, Esc na pesquisa, Enter/Delete na tabela, `lblShortcutsHint`, tooltips; barra de manutenção reescrita, subtexto do card | `DashboardController.java`, `dashboard.fxml` | Critérios 7-11 do dashboard | T1-T3, T7a, T11 (depois de T9, mesmos arquivos) |
| T11 | (i) Estilos para o `ui-designer` (só CSS) | `theme-dark.css` e `docs/design/UI.md` | Cada classe abaixo existe e é aplicada; contraste verificado | todas as outras (só toca CSS) |

Classes novas para o `ui-designer` (nomes exatos; o java-dev aplica com `getStyleClass().add`):
- Formulário: `money-hint` (texto-guia do `moneyHint`), `field-error` (mensagem de erro, importância alta), `field-warning` (aviso suave, menos forte que erro), `field-info` (dica neutra: KM sugerido, vírgula automática), `calc-block` (bloco de cálculo), `calc-value-large` (faturamento e lucro em tamanho grande), `input-affix` (prefixo "R$" e sufixo "km"). `input-error` já existe.
- Dashboard: `today-card`, `today-title`, `today-profit` (maior texto do app), `today-profit-negative` (precisa de texto além da cor), `today-metric-label`, `today-metric-value`, `today-empty-text`, `empty-state` (caixa), `empty-state-title`, `empty-state-text`, `shortcut-hint` (dica discreta), `maint-help` (explicação da reserva).

Resumo de paralelismo: T1, T2, T3, T7a, T9 e T11 podem começar juntas (arquivos diferentes). Depois: T4 (após T1-T3), T5, T6, T7b, T8 em sequência (mesmos arquivos do formulário). T10 vem depois de T9. O bloco do formulário (T4 a T8) corre em paralelo com o bloco do dashboard (T9, T10).

---

## DECISÕES EM ABERTO
Nenhuma. As quatro decisões do dono estão no topo do documento.
