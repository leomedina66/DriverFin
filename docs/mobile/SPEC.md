# DriverFin Mobile — Especificação

Versão mobile (Android) do conceito do DriverFin. O app desktop JavaFX continua existindo; esta especificação cobre só o app novo.

## Objetivo
Dar ao motorista de aplicativo o **lucro real** do dia, com o mínimo de digitação: tempo e km vêm do celular, dinheiro é lançado à mão.

## Quem usa
Motorista de aplicativo (Uber, 99, outros) que usa o celular no carro, muitas vezes com uma mão só e com pouca bateria.

## Decisões já tomadas
- Plataforma: **só Android**.
- Receita e custos: **lançamento manual**. Não há integração com Uber, 99 ou qualquer API de terceiros.
- Tempo e quilometragem: **automáticos**, pelo GPS e pelo relógio do celular.
- Turno: botão **Iniciar / Encerrar**.
- Falha de GPS: o motorista **pode corrigir o km** no fim do turno antes de salvar.
- Dados ficam **só no celular** (sem nuvem nem conta).
- Nível de autonomia: **3** (os agentes constroem).

## Funcionalidades

### F1. Turno com rastreio automático
- Tocar em **Iniciar** grava a data e a hora de início e começa a medir a distância por GPS, mesmo com a tela apagada ou o app em segundo plano.
- Durante o turno, uma notificação fixa mostra tempo decorrido e km, e tem o botão **Encerrar**.
- Tocar em **Encerrar** grava a hora final e a distância total.
- Se o app ou o celular fechar com um turno aberto, o turno é recuperado ao abrir de novo, sem perder o horário de início.
- Critérios de aceite:
  - Só existe um turno aberto por vez.
  - O km acumulado ignora leituras de GPS imprecisas e saltos impossíveis de posição.
  - Sem permissão de localização, o app explica por quê e oferece digitar o km à mão no fim do turno.

### F2. Fechamento do turno
- Ao encerrar, o app mostra: horário de início e fim, duração e km medido (editável).
- O motorista informa a **receita bruta** por fonte (Uber, 99, outros) e os **custos** (combustível, alimentação, outros).
- Critérios de aceite:
  - Valores aceitam vírgula ou ponto nos decimais, não podem ser negativos e são arredondados a centavos.
  - Km editado precisa ser maior ou igual a zero.
  - O turno só é salvo depois de confirmar.

### F3. Reserva de manutenção
- Reserva = km do turno x taxa em R$/km, configurável (padrão 0,25, máximo 100), lembrada entre usos.
- Critério de aceite: mudar a taxa não altera turnos já salvos.

### F4. Painel financeiro
- Mostra, para o período escolhido: receita bruta, custos diretos, reserva de manutenção, **lucro real**, lucro por hora, lucro por km, receita bruta por hora e por km.
- Períodos: hoje, últimos 7 dias, este mês, mês passado, intervalo personalizado.
- Critério de aceite: com zero horas ou zero km, os valores por hora e por km aparecem como "—" e nunca causam erro.

### F5. Histórico de turnos
- Lista os turnos do período, com opção de editar e apagar (com confirmação).
- Gráfico diário comparando receita, custos e lucro.

### F6. Relatório e backup
- Exportar PDF dos turnos do período, compartilhável pelo celular.
- Backup e restauração do banco de dados em arquivo, escolhendo o local.

## Requisitos não funcionais
- Tudo funciona **offline**.
- Economia de bateria: o rastreio usa a precisão mínima necessária.
- Privacidade: a localização nunca sai do celular, e o app **não guarda o trajeto**, só a distância total.
- Textos em português do Brasil, valores em R$.

## Fora de escopo
- iPhone.
- Integração com Uber, 99 ou leitura de notificações e da tela de outros apps.
- Sincronização em nuvem e contas de usuário.
- Mapa, rotas e navegação.
- Importação de dados do app desktop (pode virar um pedido futuro).

## Pontos a decidir com o `architect`
- Linguagem e framework (sugestão inicial: Kotlin nativo, já que é só Android).
- Onde fica o projeto: pasta nova ao lado do desktop ou repositório separado.
